package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.repository.*;
import br.com.tentaculomimico.security.*;
import br.com.tentaculomimico.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@WebMvcTest({PagamentoController.class,WebhookMatriculaController.class})
@Import(SecurityConfig.class)
class PagamentoFluxoMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean MatriculaService matriculas;
    @MockitoBean CursoRepository cursos;
    @MockitoBean PagamentoMatriculaRepository registros;
    @MockitoBean SessaoService sessao;
    @MockitoBean PagamentoMatriculaService pagamentos;
    @MockitoBean ConfirmacaoPagamentoService confirmacao;
    @MockitoBean OAuth2LoginSucessoHandler oauth;
    @BeforeEach void preparar() {
        var aluno = new Usuario(); aluno.setTipoUsuario(br.com.tentaculomimico.model.enums.TipoUsuario.ALUNO);
        when(sessao.usuarioLogado()).thenReturn(aluno);
        var m=new Matricula("curso","aluno","Aluno");m.setId("matricula");m.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        when(matriculas.buscarMatriculaDoAlunoLogado("matricula")).thenReturn(m);
        var c=new Curso();c.setId("curso");c.setNome("Curso");c.setPreco(new BigDecimal("129.90"));
        when(cursos.findById("curso")).thenReturn(Optional.of(c));
        when(registros.findByMatriculaId("matricula")).thenReturn(Optional.empty());
    }
    @Test void resumoERetornoAdulteradoNaoCriamNemConfirmam() throws Exception {
        mvc.perform(get("/pagamentos/matricula").param("status","PAID").param("pagamentoConcluido","true"))
                .andExpect(status().isOk()).andExpect(model().attribute("pagamentoConcluido",false))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("PIX-SIMULADO"))))
                .andExpect(content().string(not(containsString("/confirmar"))));
        verifyNoInteractions(pagamentos,confirmacao);
    }
    @Test void csrfObrigatorioE303SomenteSandbox() throws Exception {
        mvc.perform(post("/pagamentos/matricula/iniciar")).andExpect(status().isForbidden());verifyNoInteractions(pagamentos);
        when(pagamentos.iniciar("matricula")).thenReturn(URI.create("https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste"));
        mvc.perform(post("/pagamentos/matricula/iniciar").with(csrf())).andExpect(status().isSeeOther())
                .andExpect(header().string("Location","https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste"));
        when(pagamentos.iniciar("matricula")).thenReturn(URI.create("https://pagamento.pagbank.com.br/pagamento?code=teste"));
        mvc.perform(post("/pagamentos/matricula/iniciar").with(csrf())).andExpect(status().isSeeOther())
                .andExpect(header().string("Location","/pagamentos/matricula"));
    }
    @Test void anonimoEOutroAlunoNaoAcessamResumo() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(null);
        mvc.perform(get("/pagamentos/matricula")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/pagamentos/matricula/iniciar").with(csrf())).andExpect(header().string("Location","/login"));
        verifyNoInteractions(pagamentos);
        when(sessao.usuarioLogado()).thenReturn(new Usuario());
        when(matriculas.buscarMatriculaDoAlunoLogado("matricula")).thenThrow(new RuntimeException());
        mvc.perform(get("/pagamentos/matricula")).andExpect(redirectedUrl("/cursos"));
    }
    @Test void confirmacaoManualNaoExisteEFalhaNaoAtiva() throws Exception {
        mvc.perform(post("/pagamentos/matricula/confirmar").with(csrf())).andExpect(status().isNotFound());
        when(pagamentos.iniciar("matricula")).thenThrow(new IllegalStateException());
        mvc.perform(post("/pagamentos/matricula/iniciar").with(csrf())).andExpect(status().isSeeOther())
                .andExpect(flash().attributeExists("erroPagamento"));verifyNoInteractions(confirmacao);
    }
    @Test void webhookPublicoSemSessaoOuCsrfPreservaBytesEExigeAssinatura() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(null);
        byte[] corpo="{ \"reference_id\": \"teste\" }\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(post(WebhookMatriculaController.ROTA).contentType("application/json").content(corpo)
                .header("x-authenticity-token","assinatura")).andExpect(status().isNoContent())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(result.getRequest().getSession(false)));
        verify(confirmacao).processarNotificacao(eq(corpo),eq("assinatura"));
        doThrow(new ConfirmacaoPagamentoService.AssinaturaInvalida()).when(confirmacao).processarNotificacao(any(byte[].class),isNull());
        mvc.perform(post(WebhookMatriculaController.ROTA).contentType("application/json").content(corpo)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pagamentos, sessao);
    }
}
