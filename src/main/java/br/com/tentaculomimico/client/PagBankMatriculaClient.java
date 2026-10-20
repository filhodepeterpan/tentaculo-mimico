package br.com.tentaculomimico.client;

import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class PagBankMatriculaClient implements CheckoutMatriculaGateway, ConsultaCheckoutPagBank {
    public static final String ENDPOINT = "https://sandbox.api.pagseguro.com/checkouts";
    private static final Logger LOG = LoggerFactory.getLogger(PagBankMatriculaClient.class);
    private static final JsonMapper JSON = new JsonMapper();
    private static final Pattern SAFE_ERROR_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}");
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    private final RestClient http;
    private final MatriculaPagBankProperties config;

    public PagBankMatriculaClient(@Qualifier("pagBankRestClient") RestClient http, MatriculaPagBankProperties config) {
        this.http = http;
        this.config = config;
    }

    @Override
    public Criado criar(String referencia, String nome, int centavos, String retorno, String pagamento, String checkout) {
        try {
            if (centavos <= 0 || centavos > 999999900 || nome == null || nome.isBlank() || nome.length() > 100)
                throw new IllegalArgumentException("invalid_request");
            var body = Map.of("reference_id", referencia,
                    "expiration_date", Instant.now().plusSeconds(7200).toString(), "customer_modifiable", true,
                    "items", List.of(Map.of("name", nome, "quantity", 1, "unit_amount", centavos)),
                    "redirect_url", retorno, "return_url", retorno,
                    "payment_notification_urls", List.of(pagamento), "notification_urls", List.of(checkout),
                    "payment_methods", List.of(Map.of("type", "CREDIT_CARD"), Map.of("type", "PIX")),
                    "payment_methods_configs", List.of(Map.of("type", "CREDIT_CARD", "config_options",
                            List.of(Map.of("option", "INSTALLMENTS_LIMIT", "value", "1")))));

            ResponseEntity<JsonNode> respostaHttp = http.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(config.token())).body(body).retrieve().toEntity(JsonNode.class);
            JsonNode resposta = respostaHttp.getBody();
            if (resposta == null) throw rejeicaoCheckout("empty_response_body");

            String id = resposta.path("id").asText();
            String status = resposta.path("status").asText();
            String payHref = payHref(resposta);
            LOG.info("PagBank matrícula POST /checkouts response: HTTP {}, checkoutId={}, status={}, referenceMatches={}, expirationProvided={}, payLinkPresent={}",
                    respostaHttp.getStatusCode().value(), safeCheckoutId(id), safeStatus(status),
                    referencia != null && referencia.equals(resposta.path("reference_id").asText()),
                    !resposta.path("expiration_date").asText().isBlank(), payHref != null);

            if (!idValido(id)) throw rejeicaoCheckout("invalid_checkout_id");
            if (!Objects.equals(referencia, resposta.path("reference_id").asText())) throw rejeicaoCheckout("reference_mismatch");
            if (!"ACTIVE".equals(status)) throw rejeicaoCheckout("unexpected_checkout_status");
            Instant expiracao = instante(resposta.path("expiration_date").asText());
            if (expiracao == null || !expiracao.isAfter(Instant.now())) throw rejeicaoCheckout("invalid_or_expired_checkout");
            if (payHref == null) throw rejeicaoCheckout("pay_link_missing");

            URI pay;
            try {
                pay = validarPay(payHref);
            } catch (RuntimeException e) {
                throw rejeicaoCheckout("pay_link_not_allowlisted");
            }
            return new Criado(id, pay, expiracao);
        } catch (RestClientResponseException e) {
            logHttpFailure("POST /checkouts", e);
            throw new Indisponivel();
        } catch (RestClientException e) {
            LOG.warn("PagBank matrícula POST /checkouts communication failed: exceptionType={}", e.getClass().getSimpleName());
            throw new Indisponivel();
        } catch (Indisponivel e) {
            throw e;
        } catch (RuntimeException e) {
            LOG.warn("PagBank matrícula POST /checkouts could not produce a usable Checkout: exceptionType={}",
                    e.getClass().getSimpleName());
            throw new Indisponivel();
        }
    }

    @Override
    public Consulta consultar(String id) {
        try {
            validarId(id);
            ResponseEntity<JsonNode> respostaHttp = http.get().uri(ENDPOINT + "/" + id + "?limit=100")
                    .headers(h -> h.setBearerAuth(config.token())).retrieve().toEntity(JsonNode.class);
            JsonNode resposta = respostaHttp.getBody();
            if (resposta == null) throw new IllegalArgumentException("empty_response_body");
            if (!id.equals(resposta.path("id").asText())) throw new IllegalArgumentException("checkout_id_mismatch");

            List<Pagamento> pagamentos = new ArrayList<>();
            // Não interpretar uma página possivelmente incompleta como ausência de pagamento.
            if (resposta.path("orders").size() >= 100) throw new IllegalArgumentException("checkout_page_incomplete");
            // A consulta de Checkout inclui os pedidos associados; nunca seguir seus links /orders.
            for (JsonNode pedido : resposta.path("orders")) lerCobrancas(pedido.path("charges"), pagamentos);
            lerCobrancas(resposta.path("charges"), pagamentos);
            LOG.info("PagBank matrícula GET checkout response: HTTP {}, checkoutId={}, status={}, paymentCount={}",
                    respostaHttp.getStatusCode().value(), safeCheckoutId(id), safeStatus(resposta.path("status").asText()),
                    pagamentos.size());
            return new Consulta(id, resposta.path("reference_id").asText(), resposta.path("status").asText(),
                    instante(resposta.path("expiration_date").asText()), List.copyOf(pagamentos));
        } catch (RestClientResponseException e) {
            logHttpFailure("GET /checkouts/{checkout_id}", e);
            throw new Indisponivel();
        } catch (RestClientException e) {
            LOG.warn("PagBank matrícula GET checkout communication failed: exceptionType={}", e.getClass().getSimpleName());
            throw new Indisponivel();
        } catch (RuntimeException e) {
            LOG.warn("PagBank matrícula GET checkout response unusable: checkoutId={}, exceptionType={}",
                    safeCheckoutId(id), e.getClass().getSimpleName());
            throw new Indisponivel();
        }
    }

    public static void lerCobrancas(JsonNode charges, List<Pagamento> destino) {
        for (JsonNode c : charges) {
            JsonNode valor = c.path("amount").path("value");
            if (!valor.isIntegralNumber() || !valor.canConvertToLong()) throw new IllegalArgumentException();
            destino.add(new Pagamento(c.path("id").asText(), c.path("status").asText(), valor.asLong(),
                    c.path("amount").path("currency").asText(), c.path("payment_method").path("type").asText(),
                    instante(c.path("paid_at").asText())));
        }
    }

    public static URI validarPay(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"pagamento.sandbox.pagbank.com.br".equals(uri.getHost())
                || !"/pagamento".equals(uri.getRawPath()) || uri.getRawUserInfo() != null || uri.getPort() != -1
                || uri.getRawFragment() != null || uri.getRawQuery() == null
                || !uri.getRawQuery().matches("code=[A-Za-z0-9_-]+")) throw new Indisponivel();
        return uri;
    }

    public static void validarId(String id) {
        if (!idValido(id)) throw new Indisponivel();
    }

    private static boolean idValido(String id) {
        return id != null && id.matches("CHEC_[A-Za-z0-9-]{4,100}");
    }

    private static String payHref(JsonNode resposta) {
        for (JsonNode link : resposta.path("links")) {
            if ("PAY".equals(link.path("rel").asText()) && "GET".equals(link.path("method").asText())) {
                JsonNode href = link.path("href");
                return href.isTextual() ? href.asText() : null;
            }
        }
        return null;
    }

    private static Indisponivel rejeicaoCheckout(String motivo) {
        LOG.warn("PagBank matrícula Checkout response rejected locally: reason={}", motivo);
        return new Indisponivel();
    }

    private static void logHttpFailure(String operacao, RestClientResponseException erro) {
        LOG.warn("PagBank matrícula {} failed: HTTP {}, errorCode={}, requestId={}", operacao,
                erro.getStatusCode().value(), codigoErro(erro.getResponseBodyAsByteArray()),
                requestId(erro.getResponseHeaders()));
    }

    private static String codigoErro(byte[] corpo) {
        if (corpo == null || corpo.length == 0 || corpo.length > 8192) return "unavailable";
        try {
            JsonNode json = JSON.readTree(corpo);
            JsonNode erros = json.path("error_messages");
            if (erros.isArray() && !erros.isEmpty()) {
                JsonNode primeiro = erros.get(0);
                for (String campo : List.of("error", "code", "error_code")) {
                    String codigo = primeiro.path(campo).asText();
                    if (SAFE_ERROR_CODE.matcher(codigo).matches()) return codigo;
                }
            }
            for (String campo : List.of("error", "code", "error_code")) {
                String codigo = json.path(campo).asText();
                if (SAFE_ERROR_CODE.matcher(codigo).matches()) return codigo;
            }
        } catch (RuntimeException ignored) {
            // O corpo bruto do gateway nunca é registrado.
        }
        return "unavailable";
    }

    private static String requestId(HttpHeaders headers) {
        if (headers == null) return "unavailable";
        for (String header : List.of("x-request-id", "x-pagbank-request-id", "x-correlation-id")) {
            String value = headers.getFirst(header);
            if (value != null && SAFE_REQUEST_ID.matcher(value).matches()) return value;
        }
        return "unavailable";
    }

    private static String safeCheckoutId(String id) {
        return idValido(id) ? id : "unavailable";
    }

    private static String safeStatus(String status) {
        return status != null && status.matches("[A-Z_]{1,32}") ? status : "unknown";
    }

    private static Instant instante(String texto) {
        return texto == null || texto.isBlank() ? null : OffsetDateTime.parse(texto).toInstant();
    }

    public static class Indisponivel extends RuntimeException {
        public Indisponivel() { super("Pagamento temporariamente indisponível. Aguarde a conciliação antes de tentar novamente."); }
    }
}
