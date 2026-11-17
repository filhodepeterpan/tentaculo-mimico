package br.com.tentaculomimico.client;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutCriado;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class PagBankClient {
    private static final Logger LOG = LoggerFactory.getLogger(PagBankClient.class);
    private static final JsonMapper JSON = new JsonMapper();
    private static final Pattern ID_CHECKOUT = Pattern.compile("CHEC_[A-Za-z0-9-]{1,64}");
    private static final Pattern ID_PEDIDO = Pattern.compile("ORDE_[A-Za-z0-9-]{1,64}");
    private static final Pattern CODIGO_SEGURO = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> HOSTS_PRODUCAO = Set.of("pagamento.pagbank.com.br", "pagamento.pagseguro.uol.com.br");
    private static final Set<String> HOSTS_SANDBOX = Set.of("pagamento.sandbox.pagbank.com.br", "sandbox.pagamento.pagseguro.uol.com.br",
            "sandbox.pagamento.pagbank.com.br", "pagamento.pagbank.com.br", "pagamento.pagseguro.uol.com.br");
    private final RestClient http;
    private final PagBankProperties properties;

    public PagBankClient(@Qualifier("pagBankRestClient") RestClient http, PagBankProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    /** Checkout criado: id (CHEC_..., pode ser null se o PagBank não devolver) e link de pagamento. */
    public record CheckoutAberto(String id, URI pay) {}

    /** Usado pela doação: só interessa o link de pagamento. */
    public URI criar(CheckoutRequest checkout) {
        return criarCompleto(checkout).pay();
    }

    public CheckoutAberto criarCompleto(CheckoutRequest checkout) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new CheckoutIndisponivelException();
        }
        try {
            CheckoutCriado resposta = http.post().uri(properties.checkoutUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.token()))
                    .body(checkout).retrieve().body(CheckoutCriado.class);
            if (resposta == null || resposta.links() == null) throw new CheckoutIndisponivelException();
            URI pay = resposta.links().stream().filter(link -> "PAY".equals(link.rel()))
                    .map(link -> validarPay(link.href())).findFirst().orElseThrow(CheckoutIndisponivelException::new);
            String id = resposta.id() != null && ID_CHECKOUT.matcher(resposta.id()).matches() ? resposta.id() : null;
            return new CheckoutAberto(id, pay);
        } catch (RestClientResponseException e) {
            // O navegador só recebe um erro genérico. Mesmo no log, nunca exibir corpo nem headers da API.
            LOG.warn("Falha ao criar checkout PagBank ({}): HTTP {}, código {}",
                    properties.environment(), e.getStatusCode().value(), codigoErro(e.getResponseBodyAsByteArray()));
            throw new CheckoutIndisponivelException();
        } catch (RestClientException | IllegalArgumentException e) {
            // Nunca registrar corpo de resposta ou headers: podem conter dados sensíveis.
            LOG.warn("Falha de comunicação ou resposta inválida ao criar checkout PagBank");
            throw new CheckoutIndisponivelException();
        }
    }

    /** Consulta um checkout (e os pagamentos associados a ele) direto na API do PagBank. */
    public JsonNode consultarCheckout(String checkoutId) {
        if (checkoutId == null || !ID_CHECKOUT.matcher(checkoutId).matches()) throw new ConsultaIndisponivelException();
        return consultar(properties.checkoutUri(checkoutId));
    }

    /** Consulta um pedido na API do PagBank. É a fonte de verdade: nunca confiamos só no corpo do webhook. */
    public JsonNode consultarPedido(String pedidoId) {
        if (pedidoId == null || !ID_PEDIDO.matcher(pedidoId).matches()) throw new ConsultaIndisponivelException();
        return consultar(properties.pedidoUri(pedidoId));
    }

    private JsonNode consultar(URI uri) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new ConsultaIndisponivelException();
        }
        try {
            String corpo = http.get().uri(uri).headers(headers -> headers.setBearerAuth(properties.token()))
                    .retrieve().body(String.class);
            if (corpo == null || corpo.isBlank() || corpo.length() > 1_000_000) throw new ConsultaIndisponivelException();
            return JSON.readTree(corpo);
        } catch (RestClientResponseException e) {
            LOG.warn("Falha ao consultar PagBank ({}): HTTP {}", properties.environment(), e.getStatusCode().value());
            throw new ConsultaIndisponivelException();
        } catch (RestClientException | IllegalArgumentException e) {
            LOG.warn("Falha de comunicação ao consultar o PagBank");
            throw new ConsultaIndisponivelException();
        } catch (ConsultaIndisponivelException e) {
            throw e;
        } catch (RuntimeException e) { // JSON inválido
            LOG.warn("Resposta inválida ao consultar o PagBank");
            throw new ConsultaIndisponivelException();
        }
    }

    static String codigoErro(byte[] corpo) {
        if (corpo.length == 0 || corpo.length > 4096) return "indisponivel";
        try {
            JsonNode mensagens = JSON.readTree(corpo).path("error_messages");
            if (!mensagens.isArray() || mensagens.isEmpty()) return "indisponivel";
            String codigo = mensagens.get(0).path("error").asText();
            return CODIGO_SEGURO.matcher(codigo).matches() ? codigo : "indisponivel";
        } catch (RuntimeException e) {
            return "indisponivel";
        }
    }

    private URI validarPay(String url) {
        if (url == null) throw new CheckoutIndisponivelException();
        URI uri = URI.create(url);
        Set<String> hosts = properties.environment() == PagBankProperties.Environment.sandbox
                ? HOSTS_SANDBOX : HOSTS_PRODUCAO;
        if (!"https".equals(uri.getScheme()) || !hosts.contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getPort() != -1 || uri.getRawFragment() != null
                || !"/pagamento".equals(uri.getPath())) {
            throw new CheckoutIndisponivelException();
        }
        return uri;
    }

    public static class CheckoutIndisponivelException extends RuntimeException { }

    /** Não foi possível consultar o PagBank agora (rede, token, id inválido...). Tentar de novo depois. */
    public static class ConsultaIndisponivelException extends RuntimeException { }
}
