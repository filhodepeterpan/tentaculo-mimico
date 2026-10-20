package br.com.tentaculomimico.client;

import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PagBankMatriculaClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PagBankMatriculaClient client = new PagBankMatriculaClient(builder.build(),
            new MatriculaPagBankProperties("sandbox-teste", "https://exemplo.org"));
    private static final String PAY = "https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste";
    private String resposta(String links) {
        return "{\"id\":\"CHEC_teste\",\"reference_id\":\"ref\",\"status\":\"ACTIVE\",\"expiration_date\":\""
                + Instant.now().plusSeconds(7200) + "\",\"links\":" + links + "}";
    }
    private void criar() { client.criar("ref", "Curso", 12990, "https://exemplo.org/retorno", "https://exemplo.org/webhook", "https://exemplo.org/webhook"); }
    @Test void criaAvulsoComDuasNotificacoesSemRecorrencia() {
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"reference_id":"ref","items":[{"name":"Curso","quantity":1,"unit_amount":12990}],
                        "return_url":"https://exemplo.org/retorno","redirect_url":"https://exemplo.org/retorno",
                        "payment_notification_urls":["https://exemplo.org/webhook"],"notification_urls":["https://exemplo.org/webhook"],
                        "payment_methods":[{"type":"CREDIT_CARD"},{"type":"PIX"}]}
                        """))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recurrence_plan"))))
                .andRespond(withSuccess(resposta("[{\"rel\":\"PAY\",\"method\":\"GET\",\"href\":\""+PAY+"\"}]"), MediaType.APPLICATION_JSON));
        criar(); server.verify();
    }
    @Test void rejeitaUrlsManipuladasEProducao() {
        for (String url : new String[]{"https://pagamento.pagbank.com.br/pagamento?code=1", "http://pagamento.sandbox.pagbank.com.br/pagamento?code=1",
                PAY+"#x", PAY+"&redirect=evil", "https://pagamento.sandbox.pagbank.com.br.evil/pagamento?code=1",
                "https://x@pagamento.sandbox.pagbank.com.br/pagamento?code=1", "https://pagamento.sandbox.pagbank.com.br:443/pagamento?code=1",
                "https://pagamento.sandbox.pagbank.com.br/%70agamento?code=1"}) {
            assertThrows(RuntimeException.class, () -> PagBankMatriculaClient.validarPay(url));
        }
    }
    @Test void rejeitaRespostaSemPayOuEstadoDesconhecido() {
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andRespond(withSuccess(resposta("[]"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andRespond(withSuccess(resposta("[]").replace("ACTIVE", "NOVO"), MediaType.APPLICATION_JSON));
        assertThrows(PagBankMatriculaClient.Indisponivel.class, this::criar);
        assertThrows(PagBankMatriculaClient.Indisponivel.class, this::criar); server.verify();
    }
    @Test void falhaETimeoutNaoRepetemPost() {
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error_messages\":[{\"error\":\"allowlist_access_required\"}]}"));
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andRespond(withServerError());
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT)).andRespond(withException(new IOException("timeout")));
        assertThrows(PagBankMatriculaClient.Indisponivel.class, this::criar);
        assertThrows(PagBankMatriculaClient.Indisponivel.class, this::criar);
        assertThrows(PagBankMatriculaClient.Indisponivel.class, this::criar); server.verify();
    }
    @Test void consultaSomenteCheckoutESanitizaPagamento() {
        server.expect(requestTo(PagBankMatriculaClient.ENDPOINT+"/CHEC_teste?limit=100")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":"CHEC_teste","reference_id":"ref","status":"INACTIVE","orders":[{"charges":[{
                        "id":"CHAR_teste","status":"PAID","amount":{"value":12990,"currency":"BRL"},
                        "payment_method":{"type":"PIX"},"paid_at":"2026-10-19T12:00:00Z"}]}]}
                        """, MediaType.APPLICATION_JSON));
        var c = client.consultar("CHEC_teste");
        assertEquals(12990, c.pagamentos().getFirst().centavos());
        assertEquals("PAID", c.pagamentos().getFirst().status()); server.verify();
        assertThrows(PagBankMatriculaClient.Indisponivel.class, () -> client.consultar("../orders/teste"));
    }
    @Test void semTokenOuBaseInvalidaFalhaFechado() {
        var config = new MatriculaPagBankProperties("", "http://localhost");
        assertThrows(IllegalStateException.class, config::token);
        assertThrows(IllegalStateException.class, () -> config.url("/retorno"));
        assertThrows(PagBankMatriculaClient.Indisponivel.class, () -> new PagBankMatriculaClient(builder.build(), config)
                .criar("ref", "Curso", 100, "https://exemplo.org", "https://exemplo.org", "https://exemplo.org"));
        server.verify();
    }
}
