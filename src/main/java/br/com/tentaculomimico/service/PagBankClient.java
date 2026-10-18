package br.com.tentaculomimico.service;

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

    public URI criar(CheckoutRequest checkout) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new CheckoutIndisponivelException();
        }
        try {
            CheckoutCriado resposta = http.post().uri(properties.checkoutUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(properties.token()))
                    .body(checkout).retrieve().body(CheckoutCriado.class);
            if (resposta == null || resposta.links() == null) throw new CheckoutIndisponivelException();
            return resposta.links().stream().filter(link -> "PAY".equals(link.rel()))
                    .map(link -> validarPay(link.href())).findFirst().orElseThrow(CheckoutIndisponivelException::new);
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
}
