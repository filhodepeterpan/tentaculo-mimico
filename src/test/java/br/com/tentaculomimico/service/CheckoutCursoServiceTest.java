package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutCursoServiceTest {
    private final MatriculaService matriculas = mock(MatriculaService.class);
    private final CursoRepository cursos = mock(CursoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PagBankClient pagbank = mock(PagBankClient.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> pagamentos = mock(MongoCollection.class);
    private final String matriculaId = new ObjectId().toHexString();
    private final Matricula matricula = new Matricula();
    private CheckoutCursoService service;

    @BeforeEach
    void preparar() {
        matricula.setId(matriculaId);
        matricula.setCursoId(new ObjectId().toHexString());
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        Curso curso = new Curso();
        curso.setNome("Teatro");
        curso.setPreco(new BigDecimal("125.90"));
        when(matriculas.buscarMatriculaDoAlunoLogado(matriculaId)).thenReturn(matricula);
        when(cursos.findById(matricula.getCursoId())).thenReturn(Optional.of(curso));
        when(mongo.getCollection("pagamentos")).thenReturn(pagamentos);
        service = new CheckoutCursoService(matriculas, cursos, mongo, pagbank,
                new PagBankProperties("token-de-teste", PagBankProperties.Environment.sandbox,
                        "https://exemplo.test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"credito", "debito"})
    void registraTentativaECriaCheckoutSemLiberarAcesso(String metodo) {
        URI destino = URI.create("https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste");
        when(pagbank.criar(any())).thenReturn(destino);
        assertEquals(destino, service.iniciar(matriculaId, metodo, "125.90"));
        ArgumentCaptor<Document> registro = ArgumentCaptor.forClass(Document.class);
        verify(pagamentos).insertOne(registro.capture());
        assertEquals("pendente", registro.getValue().getString("status_atual"));
        assertEquals(new BigDecimal("125.90"), registro.getValue().get("valor", org.bson.types.Decimal128.class).bigDecimalValue());
        assertEquals("iniciada", registro.getValue().getList("tentativas", Document.class).getFirst().getString("status"));
        ArgumentCaptor<CheckoutRequest> pedido = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(pagbank).criar(pedido.capture());
        assertEquals(12590, pedido.getValue().items().getFirst().unitAmount());
        assertEquals("credito".equals(metodo) ? "CREDIT_CARD" : "DEBIT_CARD",
                pedido.getValue().paymentMethods().getFirst().type());
        assertEquals(StatusMatricula.AGUARDANDO_PAGAMENTO, matricula.getStatusMatricula());
        verify(matriculas, never()).confirmarPagamento(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.00", "125.91", "abc"})
    void rejeitaValorDivergenteOuInvalidoAntesDeRegistrar(String valor) {
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(matriculaId, "credito", valor));
        verifyNoInteractions(mongo, pagbank);
    }

    @Test
    void impedeNovaChamadaPagBankQuandoIndiceDetectaDuplicidade() {
        doThrow(new MongoWriteException(new WriteError(11000, "duplicado", new org.bson.BsonDocument()), new ServerAddress()))
                .when(pagamentos).insertOne(any(Document.class));
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> service.iniciar(matriculaId, "credito", "125.90"));
        assertTrue(erro.getMessage().contains("pendente"));
        verifyNoInteractions(pagbank);
    }

    @Test
    void registraFalhaDoGatewaySemConfirmarMatricula() {
        when(pagbank.criar(any())).thenThrow(new PagBankClient.CheckoutIndisponivelException());
        assertThrows(IllegalStateException.class, () -> service.iniciar(matriculaId, "credito", "125.90"));
        ArgumentCaptor<Document> atualizacao = ArgumentCaptor.forClass(Document.class);
        verify(pagamentos).updateOne(any(Document.class), atualizacao.capture());
        Document campos = atualizacao.getValue().get("$set", Document.class);
        assertEquals("recusado", campos.getString("status_atual"));
        assertEquals("falha_criacao_checkout", campos.getString("tentativas.0.status"));
        verify(matriculas, never()).confirmarPagamento(any());
    }

    @Test
    void rejeitaMatriculaQueNaoAguardaPagamento() {
        matricula.setStatusMatricula(StatusMatricula.PENDENTE);
        assertThrows(IllegalStateException.class, () -> service.iniciar(matriculaId, "credito", "125.90"));
        verifyNoInteractions(mongo, pagbank);
    }

    @Test
    void rejeitaMatriculaDeOutroAluno() {
        when(matriculas.buscarMatriculaDoAlunoLogado(matriculaId)).thenThrow(new IllegalArgumentException("Matrícula não pertence ao aluno."));
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(matriculaId, "credito", "125.90"));
        verifyNoInteractions(cursos, mongo, pagbank);
    }
}
