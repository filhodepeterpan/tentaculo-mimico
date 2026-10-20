package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.*;
import br.com.tentaculomimico.config.MatriculaPagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.Criado;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PagamentoMatriculaServiceTest {
    MatriculaService matriculas=mock(MatriculaService.class);
    SessaoService sessao=mock(SessaoService.class);
    CursoRepository cursos=mock(CursoRepository.class);
    RegistroPagamentoService registros=mock(RegistroPagamentoService.class);
    ConfirmacaoPagamentoService confirmacao=mock(ConfirmacaoPagamentoService.class);
    CheckoutMatriculaGateway gateway=mock(CheckoutMatriculaGateway.class);
    PagamentoMatriculaService service=new PagamentoMatriculaService(matriculas,sessao,cursos,registros,confirmacao,gateway,
            new MatriculaPagBankProperties("sandbox-teste","https://exemplo.org"));
    Matricula m; Curso c; Usuario aluno; PagamentoMatricula p; PagamentoMatricula.Tentativa t;
    @BeforeEach void preparar() {
        aluno=new Usuario();aluno.setId("aluno");aluno.setTipoUsuario(TipoUsuario.ALUNO);
        when(sessao.exigirUsuarioLogado()).thenReturn(aluno);
        m=new Matricula("curso","aluno","Aluno");m.setId("matricula");m.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        when(matriculas.buscarMatriculaDoAlunoLogado("matricula")).thenReturn(m);
        c=new Curso();c.setNome("Curso");c.setPreco(new BigDecimal("129.90"));
        c.setStatus(StatusCurso.DISPONIVEL);c.setAtivo(true);
        when(cursos.findById("curso")).thenReturn(Optional.of(c));
        p=new PagamentoMatricula();t=new PagamentoMatricula.Tentativa();t.setReferencia("referencia");p.getTentativas().add(t);
        when(registros.reservarInicio("matricula",12990)).thenReturn(new RegistroPagamentoService.Reserva(p,t,true));
    }
    @Test void somenteDonoAlunoAguardandoPodeCriar() {
        for(var status:StatusMatricula.values()) if(status!=StatusMatricula.AGUARDANDO_PAGAMENTO) {
            m.setStatusMatricula(status); assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));
        }
        m.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);aluno.setTipoUsuario(TipoUsuario.PROFESSOR);
        assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));
        aluno.setTipoUsuario(TipoUsuario.ALUNO);
        when(matriculas.buscarMatriculaDoAlunoLogado("matricula")).thenThrow(new RuntimeException("Outro aluno"));
        assertThrows(RuntimeException.class,()->service.iniciar("matricula")); verifyNoInteractions(gateway,registros);
    }
    @Test void valorExatoSemArredondamento() {
        c.setPreco(new BigDecimal("12.999"));assertThrows(ArithmeticException.class,()->service.iniciar("matricula"));
        c.setPreco(BigDecimal.ZERO);assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));
        c.setPreco(null);assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));verifyNoInteractions(gateway,registros);
    }
    @Test void cursoIndisponivelNaoGeraCheckout() {
        c.setStatus(StatusCurso.INDISPONIVEL);
        assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));
        c.setStatus(StatusCurso.DISPONIVEL);c.setAtivo(false);
        assertThrows(IllegalArgumentException.class,()->service.iniciar("matricula"));verifyNoInteractions(gateway,registros);
    }
    @Test void persisteAntesDeRetornarPay() {
        URI pay=URI.create("https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste");
        Instant expira=Instant.now().plusSeconds(7200);
        when(gateway.criar(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString())).thenReturn(new Criado("CHEC_teste",pay,expira));
        assertEquals(pay,service.iniciar("matricula"));
        var ordem=inOrder(registros,gateway);
        ordem.verify(registros).reservarInicio("matricula",12990);
        ordem.verify(gateway).criar("referencia","Curso",12990,"https://exemplo.org/pagamentos/matricula",
                "https://exemplo.org/webhooks/pagbank/matriculas","https://exemplo.org/webhooks/pagbank/matriculas");
        ordem.verify(registros).registrarCheckout("referencia","CHEC_teste",pay.toString(),expira);
    }
    @Test void timeoutMantemHistoricoSemCriarEmRepeticao() {
        when(gateway.criar(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString())).thenThrow(new PagBankMatriculaClient.Indisponivel());
        assertThrows(PagBankMatriculaClient.Indisponivel.class,()->service.iniciar("matricula"));
        verify(registros).falharInicio("referencia");
        when(registros.reservarInicio("matricula",12990)).thenReturn(new RegistroPagamentoService.Reserva(p,t,false));
        assertThrows(IllegalStateException.class,()->service.iniciar("matricula"));
        verify(gateway,times(1)).criar(anyString(),anyString(),anyInt(),anyString(),anyString(),anyString());
    }
    @Test void naoReutilizaUrlExpiradaOuDeProducao() {
        when(registros.reservarInicio("matricula",12990)).thenReturn(new RegistroPagamentoService.Reserva(p,t,false));
        t.setCheckoutId("CHEC_teste");t.setExpiracao(Instant.now().minusSeconds(1));
        t.setPayUrl("https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste");
        when(registros.buscar("referencia")).thenReturn(p);when(registros.tentativa(p,"referencia")).thenReturn(t);
        assertThrows(IllegalStateException.class,()->service.iniciar("matricula"));
        t.setExpiracao(Instant.now().plusSeconds(60));t.setPayUrl("https://pagamento.pagbank.com.br/pagamento?code=teste");
        assertThrows(PagBankMatriculaClient.Indisponivel.class,()->service.iniciar("matricula"));verifyNoInteractions(gateway);
    }
}
