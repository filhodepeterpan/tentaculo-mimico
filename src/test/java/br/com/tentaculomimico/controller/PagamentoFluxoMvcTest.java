package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.security.OAuth2LoginSucessoHandler;
import br.com.tentaculomimico.security.SecurityConfig;
import br.com.tentaculomimico.service.CheckoutCursoService;
import br.com.tentaculomimico.service.MatriculaService;
import br.com.tentaculomimico.service.PagamentoCursoService;
import br.com.tentaculomimico.service.SessaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PagamentoController.class)
@Import(SecurityConfig.class)
class PagamentoFluxoMvcTest {
    private static final String URL_PAGBANK = "https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste";

    @Autowired MockMvc mvc;
    @MockitoBean MatriculaService matriculas;
    @MockitoBean CursoRepository cursos;
    @MockitoBean SessaoService sessao;
    @MockitoBean CheckoutCursoService checkout;
    @MockitoBean PagamentoCursoService pagamentoCurso;
    @MockitoBean OAuth2LoginSucessoHandler oauth2Handler;

    private final Matricula matricula = new Matricula();

    @BeforeEach
    void preparar() {
        Usuario aluno = new Usuario();
        aluno.setTipoUsuario(TipoUsuario.ALUNO);
        aluno.setNome("Aluno de teste");
        aluno.setId("789");
        when(sessao.usuarioLogado()).thenReturn(aluno);
        matricula.setId("123");
        matricula.setCursoId("456");
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        Curso curso = new Curso();
        curso.setId("456");
        curso.setNome("Teatro");
        curso.setPreco(new BigDecimal("125.90"));
        when(matriculas.buscarMatriculaDoAlunoLogado("123")).thenReturn(matricula);
        when(cursos.findById("456")).thenReturn(Optional.of(curso));
    }

    // ---------------- botão "Pagar" ----------------

    @Test
    void exigeLoginAntesDeCriarCheckout() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(null);
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(checkout);
    }

    @Test
    void redirecionaParaOPagBankSemAtivarAMatricula() throws Exception {
        when(checkout.iniciar("123")).thenReturn(URI.create(URL_PAGBANK));
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(URL_PAGBANK));
        verify(pagamentoCurso).reconciliar("123"); // confere se o checkout anterior já foi pago
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    @Test
    void exibeFalhaNaPaginaDoCursoSemAtivarAMatricula() throws Exception {
        when(checkout.iniciar("123")).thenThrow(new IllegalStateException("Preço do curso inválido."));
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cursos/456"))
                .andExpect(flash().attribute("erroPagamento", "Preço do curso inválido."));
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    @Test
    void mensagemGenericaQuandoOPagBankEstaIndisponivel() throws Exception {
        when(checkout.iniciar("123")).thenThrow(new PagBankClient.CheckoutIndisponivelException());
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cursos/456"))
                .andExpect(flash().attributeExists("erroPagamento"));
    }

    @Test
    void matriculaJaAtivaVaiParaATelaDeRetorno() throws Exception {
        matricula.setStatusMatricula(StatusMatricula.ATIVA);
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/pagamentos/123/retorno"));
        verifyNoInteractions(checkout);
    }

    @Test
    void matriculaQueNaoAguardaPagamentoVoltaParaOCurso() throws Exception {
        matricula.setStatusMatricula(StatusMatricula.PENDENTE);
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cursos/456"));
        verifyNoInteractions(checkout);
    }

    @Test
    void matriculaDeOutroAlunoVaiParaListaDeCursos() throws Exception {
        when(matriculas.buscarMatriculaDoAlunoLogado("123")).thenThrow(new RuntimeException("sem permissão"));
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cursos"));
        verifyNoInteractions(checkout);
    }

    @Test
    void naoExisteMaisConfirmacaoManualPeloNavegador() throws Exception {
        mvc.perform(post("/pagamentos/123/confirmar").param("metodo", "credito"))
                .andExpect(status().is4xxClientError());
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    // ---------------- tela de retorno ----------------

    @Test
    void retornoMostraConfirmacaoQuandoAMatriculaEstaAtiva() throws Exception {
        matricula.setStatusMatricula(StatusMatricula.ATIVA);
        mvc.perform(get("/pagamentos/123/retorno"))
                .andExpect(status().isOk()).andExpect(view().name("pagamento"))
                .andExpect(content().string(containsString("Teatro")))
                .andExpect(content().string(containsString("/cursos/456")));
    }

    @Test
    void retornoConfereNoPagBankEMostraEsperaQuandoAindaNaoFoiConfirmado() throws Exception {
        mvc.perform(get("/pagamentos/123/retorno"))
                .andExpect(status().isOk()).andExpect(view().name("pagamento"))
                .andExpect(content().string(containsString("Atualizar status")))
                .andExpect(content().string(containsString("/pagamentos/123/retorno")));
        verify(pagamentoCurso).reconciliar("123");
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    @Test
    void retornoExigeLogin() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(null);
        mvc.perform(get("/pagamentos/123/retorno"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(pagamentoCurso);
    }

    @Test
    void retornoDeMatriculaDeOutroAlunoVaiParaListaDeCursos() throws Exception {
        when(matriculas.buscarMatriculaDoAlunoLogado("123")).thenThrow(new RuntimeException("sem permissão"));
        mvc.perform(get("/pagamentos/123/retorno"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/cursos"));
    }
}
