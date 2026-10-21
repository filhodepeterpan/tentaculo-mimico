package br.com.tentaculomimico.controller;

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
import br.com.tentaculomimico.service.SessaoService;
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

@WebMvcTest(value = PagamentoController.class, properties = "app.pagamento.simulado=false")
@Import(SecurityConfig.class)
class PagamentoFluxoMvcTest {
    @Autowired MockMvc mvc;
    @MockitoBean MatriculaService matriculas;
    @MockitoBean CursoRepository cursos;
    @MockitoBean SessaoService sessao;
    @MockitoBean CheckoutCursoService checkout;
    @MockitoBean OAuth2LoginSucessoHandler oauth2Handler;

    @Test
    void exigeLoginAntesDeCriarCheckout() throws Exception {
        mvc.perform(post("/pagamentos/123/checkout-cartao").param("metodo", "credito"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        verifyNoInteractions(checkout);
    }

    @Test
    void renderizaPaginaComPrecoDoCursoEFormularioDeCheckout() throws Exception {
        Usuario aluno = new Usuario();
        aluno.setTipoUsuario(TipoUsuario.ALUNO);
        aluno.setNome("Aluno de teste");
        aluno.setId("789");
        when(sessao.usuarioLogado()).thenReturn(aluno);
        Matricula matricula = new Matricula();
        matricula.setId("123");
        matricula.setCursoId("456");
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        Curso curso = new Curso();
        curso.setId("456");
        curso.setNome("Teatro");
        curso.setPreco(new BigDecimal("125.90"));
        when(matriculas.buscarMatriculaDoAlunoLogado("123")).thenReturn(matricula);
        when(cursos.findById("456")).thenReturn(Optional.of(curso));
        mvc.perform(get("/pagamentos/123"))
                .andExpect(status().isOk()).andExpect(view().name("pagamento"))
                .andExpect(content().string(containsString("/pagamentos/123/checkout-cartao")))
                .andExpect(content().string(containsString("125.90")))
                .andExpect(content().string(containsString("id=\"cartao-confirmar\"")));
    }

    @Test
    void redirecionaAoPagBankSemConfirmarPagamento() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(new Usuario());
        String url = "https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste";
        when(checkout.iniciar("123", "debito", "125.90")).thenReturn(URI.create(url));
        mvc.perform(post("/pagamentos/123/checkout-cartao").param("metodo", "debito").param("valor", "125.90"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(url));
        verify(matriculas, never()).confirmarPagamento(any());
    }

    @Test
    void exibeFalhaSemLiberarAcesso() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(new Usuario());
        when(checkout.iniciar("123", "credito", "1")).thenThrow(new IllegalArgumentException("Valor divergente."));
        mvc.perform(post("/pagamentos/123/checkout-cartao").param("metodo", "credito").param("valor", "1"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/pagamentos/123"))
                .andExpect(flash().attribute("erroPagamento", "Valor divergente."));
        verify(matriculas, never()).confirmarPagamento(any());
    }

    @Test
    void perfilSemSimulacaoNaoPermiteConfirmacaoManual() throws Exception {
        when(sessao.usuarioLogado()).thenReturn(new Usuario());
        mvc.perform(post("/pagamentos/123/confirmar").param("metodo", "credito"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("erroPagamento"));
        verify(matriculas, never()).confirmarPagamento(any());
    }
}
