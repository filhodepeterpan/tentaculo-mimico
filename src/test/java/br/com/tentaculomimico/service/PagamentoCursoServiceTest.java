package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.PagBankClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PagamentoCursoServiceTest {
    private static final JsonMapper JSON = new JsonMapper();

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PagBankClient pagbank = mock(PagBankClient.class);
    private final MatriculaService matriculas = mock(MatriculaService.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> pagamentos = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> processados = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final FindIterable<Document> busca = mock(FindIterable.class);
    private final ObjectId matriculaOid = new ObjectId();
    private final String matriculaId = matriculaOid.toHexString();
    private PagamentoCursoService service;

    @BeforeEach
    void preparar() {
        when(mongo.getCollection("pagamentos")).thenReturn(pagamentos);
        when(mongo.getCollection("webhooks_processados")).thenReturn(processados);
        when(pagamentos.find(any(Bson.class))).thenReturn(busca);
        when(busca.sort(any())).thenReturn(busca);
        when(processados.countDocuments(any(Bson.class))).thenReturn(0L);
        when(matriculas.ativarPorPagamentoAprovado(matriculaId)).thenReturn(true);
        service = new PagamentoCursoService(mongo, pagbank, matriculas);
    }

    private Document pagamentoPendente() {
        return new Document("_id", new ObjectId())
                .append("matricula_id", matriculaOid)
                .append("valor", new Decimal128(new BigDecimal("125.90")))
                .append("status_atual", "pendente")
                .append("checkout_id", "CHEC_1");
    }

    private static JsonNode json(String texto) {
        return JSON.readTree(texto);
    }

    private static String pedidoPago(long valor) {
        return "{\"id\":\"ORDE_1\",\"reference_id\":\"ref1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\","
                + "\"amount\":{\"value\":" + valor + "},\"payment_method\":{\"type\":\"CREDIT_CARD\"}}]}";
    }

    // ---------------- webhook ----------------

    @Test
    void webhookConfereNaApiDoPagBankEAtivaAMatricula() {
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(json(pedidoPago(12590)));
        when(busca.first()).thenReturn(pagamentoPendente());

        service.processarNotificacao("{\"id\":\"ORDE_1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\"}]}");

        verify(pagbank).consultarPedido("ORDE_1");
        verify(pagamentos).updateOne(any(Bson.class), any(Bson.class));
        verify(matriculas).ativarPorPagamentoAprovado(matriculaId);
        verify(processados).insertOne(any(Document.class));
    }

    @Test
    void webhookNaoConfiaNoCorpoQuandoAApiDizQueNaoFoiPago() {
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(
                json("{\"id\":\"ORDE_1\",\"reference_id\":\"ref1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"DECLINED\"}]}"));
        when(busca.first()).thenReturn(pagamentoPendente());

        service.processarNotificacao("{\"id\":\"ORDE_1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\"}]}");

        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
        verify(pagamentos, never()).updateOne(any(Bson.class), any(Bson.class));
    }

    @Test
    void webhookNaoAprovaValorMenorQueOPrecoDoCurso() {
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(json(pedidoPago(100)));
        when(busca.first()).thenReturn(pagamentoPendente());

        service.processarNotificacao("{\"id\":\"ORDE_1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\"}]}");

        verify(matriculas, never()).ativarPorPagamentoAprovado(any());
        verify(pagamentos, never()).updateOne(any(Bson.class), any(Bson.class));
    }

    @Test
    void webhookAceitaValorMaiorPorCausaDoParcelamentoComJuros() {
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(json(pedidoPago(13500)));
        when(busca.first()).thenReturn(pagamentoPendente());

        service.processarNotificacao("{\"id\":\"ORDE_1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\"}]}");

        verify(matriculas).ativarPorPagamentoAprovado(matriculaId);
    }

    @Test
    void webhookReenviadoEhIgnorado() {
        when(processados.countDocuments(any(Bson.class))).thenReturn(1L);

        service.processarNotificacao("{\"id\":\"ORDE_1\",\"charges\":[{\"id\":\"CHAR_1\",\"status\":\"PAID\"}]}");

        verifyNoInteractions(pagbank, matriculas);
    }

    @Test
    void webhookSemPagamentoCorrespondenteNaoAtivaNada() {
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(json(pedidoPago(12590)));
        when(busca.first()).thenReturn(null);

        service.processarNotificacao("{\"id\":\"ORDE_1\"}");

        verifyNoInteractions(matriculas);
    }

    @Test
    void webhookNaoRegistraEventoQuandoOPagBankEstaIndisponivel() {
        when(pagbank.consultarPedido("ORDE_1")).thenThrow(new PagBankClient.ConsultaIndisponivelException());

        assertThrows(PagBankClient.ConsultaIndisponivelException.class,
                () -> service.processarNotificacao("{\"id\":\"ORDE_1\"}"));

        verify(processados, never()).insertOne(any(Document.class)); // o PagBank vai reenviar
        verifyNoInteractions(matriculas);
    }

    @Test
    void webhookIgnoraEventoQueNaoEhDePedido() {
        service.processarNotificacao("{\"id\":\"QUALQUER_1\"}");
        verifyNoInteractions(pagbank, matriculas);
    }

    @Test
    void webhookRejeitaCorpoQueNaoEhJson() {
        assertThrows(IllegalArgumentException.class, () -> service.processarNotificacao("isto nao e json {"));
        verifyNoInteractions(pagbank, matriculas);
    }

    // ---------------- retorno do aluno ----------------

    @Test
    void retornoAtivaQuandoOCheckoutTemCobrancaPaga() {
        when(busca.first()).thenReturn(null, pagamentoPendente()); // 1ª busca: aprovado; 2ª: pendente
        when(pagbank.consultarCheckout("CHEC_1")).thenReturn(
                json("{\"id\":\"CHEC_1\",\"orders\":[" + pedidoPago(12590) + "]}"));

        service.reconciliar(matriculaId);

        verify(matriculas).ativarPorPagamentoAprovado(matriculaId);
        verify(pagbank, never()).consultarPedido(any());
    }

    @Test
    void retornoConsultaOPedidoQuandoOCheckoutSoTrazOId() {
        when(busca.first()).thenReturn(null, pagamentoPendente());
        when(pagbank.consultarCheckout("CHEC_1")).thenReturn(json("{\"id\":\"CHEC_1\",\"order_ids\":[\"ORDE_1\"]}"));
        when(pagbank.consultarPedido("ORDE_1")).thenReturn(json(pedidoPago(12590)));

        service.reconciliar(matriculaId);

        verify(matriculas).ativarPorPagamentoAprovado(matriculaId);
    }

    @Test
    void retornoSemPagamentoNaoConsultaOPagBank() {
        when(busca.first()).thenReturn(null);

        service.reconciliar(matriculaId);

        verifyNoInteractions(pagbank, matriculas);
    }

    @Test
    void retornoNaoQuebraQuandoOPagBankEstaIndisponivel() {
        when(busca.first()).thenReturn(null, pagamentoPendente());
        when(pagbank.consultarCheckout("CHEC_1")).thenThrow(new PagBankClient.ConsultaIndisponivelException());

        assertDoesNotThrow(() -> service.reconciliar(matriculaId));

        verifyNoInteractions(matriculas);
    }

    @Test
    void retornoTerminaDeAtivarPagamentoJaAprovado() {
        Document aprovado = pagamentoPendente().append("status_atual", "aprovado");
        when(busca.first()).thenReturn(aprovado);

        service.reconciliar(matriculaId);

        verify(matriculas).ativarPorPagamentoAprovado(matriculaId);
        verifyNoInteractions(pagbank);
    }
}
