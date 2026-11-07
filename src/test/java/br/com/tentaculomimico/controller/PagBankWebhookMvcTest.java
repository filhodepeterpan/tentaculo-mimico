package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.security.OAuth2LoginSucessoHandler;
import br.com.tentaculomimico.security.SecurityConfig;
import br.com.tentaculomimico.service.PagamentoCursoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PagBankWebhookController.class)
@Import(SecurityConfig.class)
class PagBankWebhookMvcTest {
    private static final String CORPO = "{\"id\":\"ORDE_1\"}";

    @Autowired MockMvc mvc;
    @MockitoBean PagamentoCursoService pagamentoCurso;
    @MockitoBean OAuth2LoginSucessoHandler oauth2Handler;

    @Test
    void aceitaNotificacaoSemLoginENemCsrf() throws Exception {
        mvc.perform(post("/webhooks/pagbank").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk());
        verify(pagamentoCurso).processarNotificacao(CORPO);
    }

    @Test
    void pedeReenvioQuandoNaoConseguiuConferirNoPagBank() throws Exception {
        doThrow(new PagBankClient.ConsultaIndisponivelException()).when(pagamentoCurso).processarNotificacao(CORPO);
        mvc.perform(post("/webhooks/pagbank").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejeitaCorpoInvalido() throws Exception {
        doThrow(new IllegalArgumentException("inválido")).when(pagamentoCurso).processarNotificacao("lixo");
        mvc.perform(post("/webhooks/pagbank").contentType(MediaType.APPLICATION_JSON).content("lixo"))
                .andExpect(status().isBadRequest());
    }
}
