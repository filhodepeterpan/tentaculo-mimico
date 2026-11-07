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
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CheckoutCursoServiceTest {
    private static final URI DESTINO = URI.create("https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste");

    private final MatriculaService matriculas = mock(MatriculaService.class);
    private final CursoRepository cursos = mock(CursoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PagBankClient pagbank = mock(PagBankClient.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> pagamentos = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final FindIterable<Document> busca = mock(FindIterable.class);
    private final String matriculaId = new ObjectId().toHexString();
    private final Matricula matricula = new Matricula();
    private final Curso curso = new Curso();
    private CheckoutCursoService service;

    @BeforeEach
    void preparar() {
        matricula.setId(matriculaId);
        matricula.setCursoId(new ObjectId().toHexString());
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        curso.setNome("Teatro");
        curso.setPreco(new BigDecimal("125.90"));
        when(matriculas.buscarMatriculaDoAlunoLogado(matriculaId)).thenReturn(matricula);
        when(cursos.findById(matricula.getCursoId())).thenReturn(Optional.of(curso));
        when(mongo.getCollection("pagamentos")).thenReturn(pagamentos);
        // Nenhum pagamento pendente por padrão (busca.first() devolve null).
        when(pagamentos.find(any(Bson.class))).thenReturn(busca);
        when(busca.sort(any())).thenReturn(busca);
        service = new CheckoutCursoService(matriculas, cursos, mongo, pagbank,
                new PagBankProperties("token-de-teste", PagBankProperties.Environment.sandbox,
                        "https://exemplo.test"));
    }

    @Test
    void registraPagamentoECriaCheckoutComValorDoCursoSemLiberarAcesso() {
        when(pagbank.criarCompleto(any())).thenReturn(new PagBankClient.CheckoutAberto("CHEC_123", DESTINO));

        assertEquals(DESTINO, service.iniciar(matriculaId));

        ArgumentCaptor<Document> registro = ArgumentCaptor.forClass(Document.class);
        verify(pagamentos).insertOne(registro.capture());
        assertEquals("pendente", registro.getValue().getString("status_atual"));
        assertNull(registro.getValue().get("metodo_pagamento"));
        assertEquals(new BigDecimal("125.90"), registro.getValue().get("valor", org.bson.types.Decimal128.class).bigDecimalValue());
        assertEquals("iniciada", registro.getValue().getList("tentativas", Document.class).getFirst().getString("status"));

        ArgumentCaptor<CheckoutRequest> pedido = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(pagbank).criarCompleto(pedido.capture());
        assertEquals(12590, pedido.getValue().items().getFirst().unitAmount());
        assertNull(pedido.getValue().paymentMethods()); // todas as formas habilitadas na conta
        assertEquals("https://exemplo.test/pagamentos/" + matriculaId + "/retorno", pedido.getValue().redirectUrl());
        assertEquals(pedido.getValue().redirectUrl(), pedido.getValue().returnUrl());
        assertEquals(List.of("https://exemplo.test/webhooks/pagbank"), pedido.getValue().notificationUrls());
        assertEquals(List.of("https://exemplo.test/webhooks/pagbank"), pedido.getValue().paymentNotificationUrls());

        ArgumentCaptor<Document> atualizacao = ArgumentCaptor.forClass(Document.class);
        verify(pagamentos).updateOne(any(Bson.class), atualizacao.capture());
        Document campos = atualizacao.getValue().get("$set", Document.class);
        assertEquals("CHEC_123", campos.getString("checkout_id"));
        assertEquals(DESTINO.toString(), campos.getString("checkout_url"));

        assertEquals(StatusMatricula.AGUARDANDO_PAGAMENTO, matricula.getStatusMatricula());
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    @Test
    void semHttpsNaoEnviaUrlDeWebhook() {
        service = new CheckoutCursoService(matriculas, cursos, mongo, pagbank,
                new PagBankProperties("token-de-teste", PagBankProperties.Environment.sandbox, "http://localhost:8080"));
        when(pagbank.criarCompleto(any())).thenReturn(new PagBankClient.CheckoutAberto("CHEC_123", DESTINO));

        service.iniciar(matriculaId);

        ArgumentCaptor<CheckoutRequest> pedido = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(pagbank).criarCompleto(pedido.capture());
        assertTrue(pedido.getValue().notificationUrls().isEmpty());
        assertEquals("http://localhost:8080/pagamentos/" + matriculaId + "/retorno", pedido.getValue().redirectUrl());
    }

    @Test
    void cliqueDuploReaproveitaCheckoutAindaValido() {
        Document pendente = new Document("checkout_url", DESTINO.toString())
                .append("checkout_expira_em", Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .append("criado_em", new Date());
        when(busca.first()).thenReturn(pendente);

        assertEquals(DESTINO, service.iniciar(matriculaId));

        verifyNoInteractions(pagbank);
        verify(pagamentos, never()).insertOne(any(Document.class));
    }

    @Test
    void checkoutExpiradoEncerraOPendenteECriaOutro() {
        Document expirado = new Document("checkout_url", DESTINO.toString())
                .append("checkout_expira_em", Date.from(Instant.now().minus(1, ChronoUnit.HOURS)))
                .append("criado_em", Date.from(Instant.now().minus(3, ChronoUnit.HOURS)));
        when(busca.first()).thenReturn(expirado);
        URI novo = URI.create("https://pagamento.sandbox.pagbank.com.br/pagamento?code=novo");
        when(pagbank.criarCompleto(any())).thenReturn(new PagBankClient.CheckoutAberto("CHEC_NOVO", novo));

        assertEquals(novo, service.iniciar(matriculaId));

        verify(pagamentos).updateMany(any(Bson.class), any(Bson.class));
        verify(pagamentos).insertOne(any(Document.class));
    }

    @Test
    void impedeNovaChamadaPagBankQuandoIndiceDetectaDuplicidade() {
        doThrow(new MongoWriteException(new WriteError(11000, "duplicado", new org.bson.BsonDocument()), new ServerAddress()))
                .when(pagamentos).insertOne(any(Document.class));
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> service.iniciar(matriculaId));
        assertTrue(erro.getMessage().contains("preparado"));
        verifyNoInteractions(pagbank);
    }

    @Test
    void registraFalhaDoGatewaySemAtivarMatricula() {
        when(pagbank.criarCompleto(any())).thenThrow(new PagBankClient.CheckoutIndisponivelException());
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> service.iniciar(matriculaId));
        ArgumentCaptor<Document> atualizacao = ArgumentCaptor.forClass(Document.class);
        verify(pagamentos).updateOne(any(Bson.class), atualizacao.capture());
        Document campos = atualizacao.getValue().get("$set", Document.class);
        assertEquals("recusado", campos.getString("status_atual"));
        assertEquals("falha_criacao_checkout", campos.getString("tentativas.0.status"));
        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
    }

    @Test
    void rejeitaPrecoInvalidoAntesDeRegistrar() {
        curso.setPreco(new BigDecimal("10.999"));
        assertThrows(IllegalStateException.class, () -> service.iniciar(matriculaId));
        verifyNoInteractions(mongo, pagbank);
    }

    @Test
    void rejeitaMatriculaQueNaoAguardaPagamento() {
        matricula.setStatusMatricula(StatusMatricula.PENDENTE);
        assertThrows(IllegalStateException.class, () -> service.iniciar(matriculaId));
        verifyNoInteractions(mongo, pagbank);
    }

    @Test
    void rejeitaMatriculaDeOutroAluno() {
        when(matriculas.buscarMatriculaDoAlunoLogado(matriculaId)).thenThrow(new IllegalArgumentException("Matrícula não pertence ao aluno."));
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(matriculaId));
        verifyNoInteractions(cursos, mongo, pagbank);
    }
}
