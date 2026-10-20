package br.com.tentaculomimico.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;

@Component
public class MatriculaPagBankProperties {
    private final String token;
    private final String baseUrl;
    public MatriculaPagBankProperties(@Value("${pagbank.token:}") String token,
                                     @Value("${pagbank.public-base-url:https://google.com}") String baseUrl) {
        this.token = token;
        this.baseUrl = baseUrl;
    }
    public String token() {
        if (token == null || token.isBlank()) throw new IllegalStateException("Pagamento indisponível.");
        return token;
    }
    public String url(String caminho) {
        URI uri = URI.create(baseUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
            throw new IllegalStateException("URL pública inválida.");
        return "https://" + uri.getHost() + caminho;
    }
}
