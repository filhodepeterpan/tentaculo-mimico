package br.com.tentaculomimico.service;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PagBankClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PagBankProperties properties = new PagBankProperties("segredo", PagBankProperties.Environment.sandbox, "https://exemplo.org");
    private final PagBankClient client = new PagBankClient(builder.build(), properties);
    private final CheckoutRequest checkout = new CheckoutRequest("referencia", OffsetDateTime.now(), true,
            List.of(new CheckoutRequest.Item("Doação ao Tentáculo Mímico", 1, 3500)),
            "https://exemplo.org/doacoes?retorno=pagbank", "https://exemplo.org/doacoes?retorno=pagbank", List.of());

    @Test
    void criaCheckoutEUsaSomenteLinkPay() {
        server.expect(requestTo("https://sandbox.api.pagseguro.com/checkouts"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer segredo"))
                .andExpect(content().json("""
                        {"reference_id":"referencia","customer_modifiable":true,"items":[{"quantity":1,"unit_amount":3500}],
                         "redirect_url":"https://exemplo.org/doacoes?retorno=pagbank",
                         "return_url":"https://exemplo.org/doacoes?retorno=pagbank"}
                        """))
                .andRespond(withSuccess("""
                        {"links":[{"rel":"SELF","href":"https://api.pagseguro.com/checkouts/1"},
                                  {"rel":"PAY","href":"https://pagamento.pagseguro.uol.com.br/pagamento?code=1"}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(URI.create("https://pagamento.pagseguro.uol.com.br/pagamento?code=1"), client.criar(checkout));
        server.verify();
    }

    @Test
    void rejeitaLinkNaoOficialERespostaSemPay() {
        String[] respostas = {
                "{\"links\":[]}",
                "{\"links\":[{\"rel\":\"PAY\",\"href\":\"https://pagamento.pagbank.com.br.evil.org/pagamento\"}]}",
                "{\"links\":[{\"rel\":\"PAY\",\"href\":\"http://pagamento.pagbank.com.br/pagamento\"}]}"};
        for (String corpo : respostas) {
            server.expect(requestTo("https://sandbox.api.pagseguro.com/checkouts"))
                    .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
        }
        for (String corpo : respostas) {
            assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> client.criar(checkout));
        }
        server.verify();
    }

    @Test
    void falhaDeApiNaoGeraSucessoNemRetry() {
        server.expect(requestTo("https://sandbox.api.pagseguro.com/checkouts"))
                .andRespond(withServerError());
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> client.criar(checkout));
        server.verify();
    }

    @Test
    void resposta403NaoEntregaDetalhesAoDoador() {
        server.expect(requestTo("https://sandbox.api.pagseguro.com/checkouts"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error_messages":[{"error":"invalid_authorization_header","description":"não divulgar credenciais"}]}
                                """));
        PagBankClient.CheckoutIndisponivelException erro = assertThrows(
                PagBankClient.CheckoutIndisponivelException.class, () -> client.criar(checkout));
        assertNull(erro.getMessage());
        server.verify();
    }

    @Test
    void naoChamaApiSemToken() {
        PagBankClient semToken = new PagBankClient(builder.build(), new PagBankProperties("", PagBankProperties.Environment.sandbox, "https://exemplo.org"));
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> semToken.criar(checkout));
        server.verify();
    }

    @Test
    void producaoNaoAceitaHostDeSandbox() {
        PagBankClient producao = new PagBankClient(builder.build(),
                new PagBankProperties("segredo", PagBankProperties.Environment.production, "https://exemplo.org"));
        server.expect(requestTo("https://api.pagseguro.com/checkouts"))
                .andRespond(withSuccess("""
                        {"links":[{"rel":"PAY","href":"https://sandbox.pagamento.pagseguro.uol.com.br/pagamento?code=1"}]}
                        """, MediaType.APPLICATION_JSON));
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> producao.criar(checkout));
        server.verify();
    }

    @Test
    void extraiSomenteCodigoSaneadoDaRespostaDeErro() {
        assertEquals("allowlist_access_required", PagBankClient.codigoErro("""
                {"error_messages":[{"error":"allowlist_access_required","description":"dados privados"}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("indisponivel", PagBankClient.codigoErro("<html>Access denied</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("indisponivel", PagBankClient.codigoErro("""
                {"error_messages":[{"error":"token: secreto"}]}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
