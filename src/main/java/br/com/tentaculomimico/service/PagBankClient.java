package br.com.tentaculomimico.service;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutCriado;
import br.com.tentaculomimico.dto.pagbank.CriarCheckout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

@Component
public class PagBankClient {
    private static final Logger LOG = LoggerFactory.getLogger(PagBankClient.class);
    private static final Set<String> HOSTS_PRODUCAO = Set.of("pagamento.pagbank.com.br", "pagamento.pagseguro.uol.com.br");
    private static final Set<String> HOSTS_SANDBOX = Set.of("sandbox.pagamento.pagseguro.uol.com.br",
            "sandbox.pagamento.pagbank.com.br", "pagamento.pagbank.com.br", "pagamento.pagseguro.uol.com.br");
    private final RestClient http;
    private final PagBankProperties properties;

    public PagBankClient(@Qualifier("pagBankRestClient") RestClient http, PagBankProperties properties) {
        this.http = http;
        this.properties = properties;
    }

    public URI criar(CriarCheckout checkout) {
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
            LOG.warn("Falha ao criar checkout PagBank: HTTP {}", e.getStatusCode().value());
            throw new CheckoutIndisponivelException();
        } catch (RestClientException | IllegalArgumentException e) {
            // Nunca registrar corpo de resposta ou headers: podem conter dados sensíveis.
            LOG.warn("Falha de comunicação ou resposta inválida ao criar checkout PagBank");
            throw new CheckoutIndisponivelException();
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
