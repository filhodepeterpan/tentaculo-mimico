package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.security.OAuth2LoginSucessoHandler;
import br.com.tentaculomimico.security.SecurityConfig;
import br.com.tentaculomimico.service.AdminService;
import br.com.tentaculomimico.service.DoacaoService;
import br.com.tentaculomimico.service.SessaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DoacaoController.class, PaginasController.class})
@Import(SecurityConfig.class)
class DoacaoFluxoMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean DoacaoService doacaoService;
    @MockitoBean SessaoService sessaoService;
    @MockitoBean AdminService adminService;
    @MockitoBean OAuth2LoginSucessoHandler oauth2Handler;

    @Test
    void publicoSemCsrfPodeConsultarMasNaoCriarCheckout() throws Exception {
        mvc.perform(get("/doacoes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(result -> {
                    String html = result.getResponse().getContentAsString();
                    org.junit.jupiter.api.Assertions.assertEquals(html.indexOf("name=\"_csrf\""),
                            html.lastIndexOf("name=\"_csrf\""));
                })
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-mostrar=\"false\"")));
        mvc.perform(post("/doacoes").param("valor", "35"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(doacaoService);
    }

    @Test
    void criaCheckoutERedirecionaDiretoCom303() throws Exception {
        when(doacaoService.iniciar("35", "")).thenReturn(URI.create("https://pagamento.pagbank.com.br/pagamento?code=1"));
        mvc.perform(post("/doacoes").with(csrf()).param("valor", "35"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "https://pagamento.pagbank.com.br/pagamento?code=1"));
        verify(doacaoService).iniciar("35", "");
    }

    @Test
    void errosNaoAgradecemEFluxoLegadoNaoTemConfirmacao() throws Exception {
        when(doacaoService.iniciar(anyString(), anyString())).thenThrow(new IllegalArgumentException("Valor inválido."));
        mvc.perform(post("/doacoes").with(csrf()).param("valor", "0"))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/doacoes"))
                .andExpect(flash().attribute("erroDoacao", "Valor inválido."));
        mvc.perform(get("/pagamentos/doacao")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/doacoes"));
        mvc.perform(post("/pagamentos/doacao/confirmar")).andExpect(status().isNotFound());
    }

    @Test
    void retornoSoAcionaMarcadorVisual() throws Exception {
        mvc.perform(get("/doacoes").param("retorno", "pagbank"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-mostrar=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Agradecemos sua generosidade!")));
        mvc.perform(get("/doacoes").param("retorno", "qualquer"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-mostrar=\"false\"")));
        verifyNoInteractions(doacaoService);
    }
}
