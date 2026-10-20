package br.com.tentaculomimico.client;

import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class PagBankMatriculaClient implements CheckoutMatriculaGateway, ConsultaCheckoutPagBank {
    public static final String ENDPOINT = "https://sandbox.api.pagseguro.com/checkouts";
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
                throw new IllegalArgumentException();
            var body = Map.of("reference_id", referencia,
                    "expiration_date", Instant.now().plusSeconds(7200).toString(), "customer_modifiable", true,
                    "items", List.of(Map.of("name", nome, "quantity", 1, "unit_amount", centavos)),
                    "redirect_url", retorno, "return_url", retorno,
                    "payment_notification_urls", List.of(pagamento), "notification_urls", List.of(checkout),
                    "payment_methods", List.of(Map.of("type", "CREDIT_CARD"), Map.of("type", "PIX")),
                    "payment_methods_configs", List.of(Map.of("type", "CREDIT_CARD", "config_options",
                            List.of(Map.of("option", "INSTALLMENTS_LIMIT", "value", "1")))));
            JsonNode resposta = http.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(config.token())).body(body).retrieve().body(JsonNode.class);
            String id = resposta.path("id").asText();
            validarId(id);
            if (!referencia.equals(resposta.path("reference_id").asText()) || !"ACTIVE".equals(resposta.path("status").asText()))
                throw new IllegalArgumentException();
            Instant expiracao = instante(resposta.path("expiration_date").asText());
            if (expiracao == null || !expiracao.isAfter(Instant.now())) throw new IllegalArgumentException();
            for (JsonNode link : resposta.path("links")) {
                if ("PAY".equals(link.path("rel").asText()) && "GET".equals(link.path("method").asText()))
                    return new Criado(id, validarPay(link.path("href").asText()), expiracao);
            }
            throw new IllegalArgumentException();
        } catch (RuntimeException e) { throw new Indisponivel(); }
    }
    @Override
    public Consulta consultar(String id) {
        try {
            validarId(id);
            JsonNode resposta = http.get().uri(ENDPOINT + "/" + id + "?limit=100")
                    .headers(h -> h.setBearerAuth(config.token())).retrieve().body(JsonNode.class);
            if (!id.equals(resposta.path("id").asText())) throw new IllegalArgumentException();
            List<Pagamento> pagamentos = new ArrayList<>();
            // Não interpretar uma página possivelmente incompleta como ausência de pagamento.
            if (resposta.path("orders").size() >= 100) throw new IllegalArgumentException();
            // A consulta de Checkout inclui os pedidos associados; nunca seguir seus links /orders.
            for (JsonNode pedido : resposta.path("orders")) lerCobrancas(pedido.path("charges"), pagamentos);
            lerCobrancas(resposta.path("charges"), pagamentos);
            return new Consulta(id, resposta.path("reference_id").asText(), resposta.path("status").asText(),
                    instante(resposta.path("expiration_date").asText()), List.copyOf(pagamentos));
        } catch (RuntimeException e) { throw new Indisponivel(); }
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
        if (id == null || !id.matches("CHEC_[A-Za-z0-9-]{4,100}")) throw new Indisponivel();
    }
    private static Instant instante(String texto) {
        return texto == null || texto.isBlank() ? null : OffsetDateTime.parse(texto).toInstant();
    }
    public static class Indisponivel extends RuntimeException {
        public Indisponivel() { super("Pagamento temporariamente indisponível. Aguarde a conciliação antes de tentar novamente."); }
    }
}
