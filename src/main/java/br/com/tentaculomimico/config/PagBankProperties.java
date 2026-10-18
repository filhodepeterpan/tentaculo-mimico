package br.com.tentaculomimico.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "pagbank")
public record PagBankProperties(String token, Environment environment, String publicBaseUrl) {
    public enum Environment { sandbox, production }

    public PagBankProperties {
        if (environment == null) environment = Environment.sandbox;
    }

    public URI checkoutUri() {
        return URI.create(environment == Environment.sandbox
                ? "https://sandbox.api.pagseguro.com/checkouts"
                : "https://api.pagseguro.com/checkouts");
    }

    public URI retorno() {
        if (publicBaseUrl == null) throw new IllegalStateException("URL pública não configurada");
        try {
            URI base = URI.create(publicBaseUrl);
            if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getRawUserInfo() != null
                    || base.getPort() != -1 || base.getRawQuery() != null || base.getRawFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
                throw new IllegalStateException("URL pública inválida (use a origem HTTPS, sem caminho)");
            }
            return URI.create(base.toString().replaceAll("/$", "") + "/doacoes?retorno=pagbank");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("URL pública inválida", e);
        }
    }
}
