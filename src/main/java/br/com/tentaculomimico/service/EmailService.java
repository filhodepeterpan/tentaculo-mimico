package br.com.tentaculomimico.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envio de e-mails pela API HTTP da Resend (POST https://api.resend.com/emails).
 * Usa HTTPS na porta 443, então funciona no Render (inclusive no plano gratuito,
 * que bloqueia SMTP de saída).
 */
@Service
public class EmailService {

    private static final String RESEND_URL = "https://api.resend.com";
    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(5);
    private static final Duration TIMEOUT_RESPOSTA = Duration.ofSeconds(10);
    private static final int LIMITE_CORPO_ERRO = 300;

    /**
     * Falha ao enviar e-mail (resposta de erro da API, timeout, rede fora, etc.).
     * Substitui o antigo MailException do Spring, que vinha do starter de e-mail.
     */
    public static class EmailEnvioException extends RuntimeException {
        public EmailEnvioException(String mensagem) {
            super(mensagem);
        }

        public EmailEnvioException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }

    private final RestClient restClient;
    private final String remetente;
    private final String baseUrl;

    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${resend.from}") String remetente,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Configuração ausente: defina a variável de ambiente RESEND_API_KEY (propriedade resend.api-key).");
        }
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalStateException(
                    "Configuração ausente: defina a variável de ambiente RESEND_FROM (propriedade resend.from).");
        }

        this.remetente = remetente.trim();
        // Evita "//" no link caso a variável de ambiente termine com "/".
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT_CONEXAO)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(TIMEOUT_RESPOSTA);

        this.restClient = RestClient.builder()
                .baseUrl(RESEND_URL)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                // A Resend rejeita requisições sem User-Agent.
                .defaultHeader(HttpHeaders.USER_AGENT, "tentaculo-mimico/1.0")
                .build();
    }

    public void enviarEmailVerificacao(String destinatario, String token) {
        String link = baseUrl + "/confirmar-email?token=" + token;

        enviar(destinatario,
                "Confirme seu e-mail - Tentáculo Mímico",
                "Olá!\n\n" +
                "Obrigado por se cadastrar. Para ativar sua conta, confirme seu e-mail pelo link abaixo:\n\n" +
                link + "\n\n" +
                "O link vale por 24 horas. Se você não criou esta conta, pode ignorar este e-mail.");
    }

    public void enviarEmailRecuperacao(String destinatario, String token) {
        String link = baseUrl + "/nova-senha?token=" + token;

        enviar(destinatario,
                "Recuperação de Senha",
                "Olá!\n\n" +
                "Recebemos um pedido para redefinir a sua senha.\n" +
                "Clique no link abaixo para criar uma nova:\n\n" +
                link + "\n\n" +
                "Se não foi você que solicitou isso, pode ignorar este e-mail com segurança.");
    }

    /** Só retorna normalmente se a Resend respondeu 2xx. Qualquer outra situação lança EmailEnvioException. */
    private void enviar(String destinatario, String assunto, String texto) {
        Map<String, Object> corpo = Map.of(
                "from", remetente,
                "to", List.of(destinatario),
                "subject", assunto,
                "text", texto
        );

        try {
            restClient.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(corpo)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // A API respondeu 4xx/5xx. A chave de API não aparece na resposta nem é registrada.
            String detalhe = e.getResponseBodyAsString();
            if (detalhe.length() > LIMITE_CORPO_ERRO) {
                detalhe = detalhe.substring(0, LIMITE_CORPO_ERRO) + "...";
            }
            throw new EmailEnvioException(
                    "A Resend recusou o envio (HTTP " + e.getStatusCode().value() + "): " + detalhe, e);
        } catch (RestClientException e) {
            // Falha de rede, timeout, conexão interrompida, resposta ilegível.
            throw new EmailEnvioException("Falha de comunicação com a Resend: " + e.getMessage(), e);
        }
    }
}
