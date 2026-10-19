package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.ConsultaCheckoutPagBank;
import br.com.tentaculomimico.config.*;
import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.*;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.repository.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.testcontainers.mongodb.MongoDBContainer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Mongo real isolado: exercita índices, CAS e recuperação sem transação distribuída. Requer Docker. */
class PagamentoMongoTest {
    static MongoDBContainer container;
    static MongoClient client;
    MongoTemplate mongo;
    PagamentoMatriculaRepository repository;
    WebhookProcessadoRepository eventos;
    RegistroPagamentoService registros;
    ConsultaCheckoutPagBank gateway;
    ConfirmacaoPagamentoService confirmacao;
    Matricula matricula;
    String referencia;
    @BeforeAll static void iniciarMongo() {
        container = new MongoDBContainer("mongo:7.0.14");
        container.start();
        container.copyFileToContainer(org.testcontainers.utility.MountableFile.forHostPath("docs/ddl_banco/setup_database.js"), "/tmp/setup.js");
        client = MongoClients.create(container.getReplicaSetUrl());
    }
    @AfterAll static void encerrarMongo() { if (client != null) client.close(); if (container != null) container.stop(); }
    @BeforeEach void preparar() throws Exception {
        client.getDatabase("pagamento_test").drop();
        assertEquals(0, container.execInContainer("mongosh", "pagamento_test", "--quiet", "/tmp/setup.js").getExitCode());
        mongo = spy(new MongoTemplate(client, "pagamento_test"));
        new PagamentoIndices(mongo).afterPropertiesSet();
        var factory = new MongoRepositoryFactory(mongo);
        repository = factory.getRepository(PagamentoMatriculaRepository.class);
        eventos = factory.getRepository(WebhookProcessadoRepository.class);
        registros = new RegistroPagamentoService(repository);
        gateway = mock(ConsultaCheckoutPagBank.class);
        confirmacao = new ConfirmacaoPagamentoService(new MatriculaPagBankProperties("sandbox-teste", "https://exemplo.org"), gateway, registros, eventos, mongo);
        matricula = new Matricula(new ObjectId().toHexString(), new ObjectId().toHexString(), "Teste");
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        mongo.insert(matricula);
        referencia = registros.reservarInicio(matricula.getId(), 12990).tentativa().getReferencia();
    }
    Consulta consulta(String status, String pagamento) {
        return new Consulta("CHEC_teste", referencia, status, Instant.now().minusSeconds(1),
                pagamento == null ? List.of() : List.of(new Pagamento("CHAR_teste", pagamento, 12990, "BRL", "PIX", Instant.parse("2026-10-19T12:00:00Z"))));
    }
    byte[] corpo() { return ("{\"id\":\"CHEC_teste\",\"reference_id\":\""+referencia+"\",\"status\":\"EXPIRED\"}").getBytes(StandardCharsets.UTF_8); }
    String assinatura(byte[] bytes) {
        return ConfirmacaoPagamentoService.hash(("sandbox-teste-"+new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
    }
    void entregar() { byte[] bytes = corpo(); confirmacao.processarNotificacao(bytes, assinatura(bytes)); }
    @Test void mapeamentoMongoPreservaTiposEIndices() {
        Document p = mongo.getCollection("pagamentos").find().first();
        assertInstanceOf(ObjectId.class, p.get("matricula_id"));
        assertInstanceOf(Decimal128.class, p.get("valor"));
        assertInstanceOf(Date.class, p.get("mes_referencia"));
        assertInstanceOf(Date.class, p.get("data_vencimento"));
        assertEquals("PENDENTE", p.get("status_atual"));
        assertFalse(p.toJson().contains("sandbox-teste"));
        assertEquals(1, repository.count());
    }
    @Test void cliquesConcorrentesCriamUmaUnicaReserva() throws Exception {
        String id = new ObjectId().toHexString();
        try (var pool = Executors.newFixedThreadPool(12)) {
            var inicio = new CountDownLatch(1);
            List<Future<RegistroPagamentoService.Reserva>> futures = new ArrayList<>();
            for (int i=0;i<12;i++) futures.add(pool.submit(() -> { inicio.await(); return registros.reservarInicio(id, 100); }));
            inicio.countDown();
            int criadores = 0;
            Set<String> referencias = new HashSet<>();
            for (var f : futures) { var r = f.get(20, TimeUnit.SECONDS); if (r.criar()) criadores++; referencias.add(r.tentativa().getReferencia()); }
            assertEquals(1, criadores); assertEquals(1, referencias.size());
        }
    }
    @Test void timeoutNaoAutorizaOutroCheckoutEWebhookPrecoceRecupera() {
        registros.falharInicio(referencia);
        var r = registros.reservarInicio(matricula.getId(),12990);
        assertFalse(r.criar()); assertEquals(referencia,r.tentativa().getReferencia());
        assertNotNull(r.tentativa().getFalhouEm());
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE", "PAID"));
        entregar();
        registros.registrarCheckout(referencia,"CHEC_teste","https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste",Instant.now().plusSeconds(60));
        assertEquals(PagamentoMatricula.EstadoTentativa.PAID,registros.tentativa(registros.buscar(referencia),referencia).getStatus());
        assertEquals(StatusMatricula.ATIVA,mongo.findById(matricula.getId(),Matricula.class).getStatusMatricula());
    }
    @Test void waitingPaidDuplicataEForaDeOrdem() {
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("ACTIVE","WAITING"));
        entregar(); assertFalse(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID"));
        entregar(); entregar();
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("ACTIVE","WAITING"));
        entregar();
        assertEquals(PagamentoMatricula.Estado.APROVADO,registros.buscar(referencia).getStatus());
        assertTrue(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
        assertEquals(2,eventos.count());
    }
    @Test void expiracaoSemPagamentoPermiteUmaNovaTentativa() {
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("EXPIRED",null)); entregar();
        var nova = registros.reservarInicio(matricula.getId(),12990);
        assertTrue(nova.criar()); assertNotEquals(referencia,nova.tentativa().getReferencia());
        assertFalse(registros.reservarInicio(matricula.getId(),12990).criar());
        assertEquals(2,registros.buscar(referencia).getTentativas().size());
        assertFalse(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
    }
    @Test void recusaCancelamentoEAnaliseNaoLiberamNemCriamCobrancaParalela() {
        for (String status : List.of("DECLINED","CANCELED","IN_ANALYSIS")) {
            when(gateway.consultar("CHEC_teste")).thenReturn(consulta("ACTIVE",status)); entregar();
            assertFalse(registros.reservarInicio(matricula.getId(),12990).criar());
            assertFalse(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
        }
    }
    @Test void pagamentoTardioNaoReativaCanceladaOuRecusada() {
        for (StatusMatricula status : List.of(StatusMatricula.CANCELADA,StatusMatricula.RECUSADA)) {
            eventos.deleteAll(); matricula.setStatusMatricula(status); mongo.save(matricula);
            when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID")); entregar();
            assertEquals(status,mongo.findById(matricula.getId(),Matricula.class).getStatusMatricula());
            assertFalse(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
            assertTrue(registros.buscar(referencia).isConciliacaoNecessaria());
        }
    }
    @Test void falhaEntreAprovacaoEAtivacaoPermiteReentrega() {
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID"));
        doThrow(new IllegalStateException("falha transitória")).doCallRealMethod().when(mongo)
                .updateFirst(any(Query.class),any(UpdateDefinition.class),eq(Matricula.class));
        assertThrows(IllegalStateException.class,this::entregar);
        assertEquals(0,eventos.count()); assertEquals(PagamentoMatricula.Estado.APROVADO,registros.buscar(referencia).getStatus());
        entregar(); assertTrue(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado()); assertEquals(1,eventos.count());
    }
    @Test void assinaturaValidaCorpoBrutoERejeitaAlteracao() {
        byte[] bytes=corpo(); confirmacao.validarAssinatura(bytes,assinatura(bytes));
        assertThrows(ConfirmacaoPagamentoService.AssinaturaInvalida.class,()->confirmacao.validarAssinatura((new String(bytes,StandardCharsets.UTF_8)+" ").getBytes(StandardCharsets.UTF_8),assinatura(bytes)));
        assertThrows(ConfirmacaoPagamentoService.AssinaturaInvalida.class,()->confirmacao.validarAssinatura(bytes,null));
        verifyNoInteractions(gateway); assertEquals(0,eventos.count());
    }
    @Test void divergenciaEEstadoDesconhecidoNaoLiberamAcesso() {
        for (Consulta c : List.of(new Consulta("CHEC_teste","doacao","ACTIVE",Instant.now(),List.of()),
                consulta("DESCONHECIDO","PAID"), consulta("ACTIVE","NOVO"),
                new Consulta("CHEC_teste",referencia,"INACTIVE",Instant.now(),List.of(new Pagamento("CHAR_teste","PAID",1,"BRL","PIX",Instant.now()))),
                new Consulta("CHEC_teste",referencia,"INACTIVE",Instant.now(),List.of(new Pagamento("CHAR_teste","PAID",12990,"USD","PIX",Instant.now()))))) {
            when(gateway.consultar("CHEC_teste")).thenReturn(c);
            assertThrows(IllegalArgumentException.class,this::entregar);
        }
        assertEquals(0,eventos.count()); assertFalse(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
    }
    @Test void entregasConcorrentesSaoIdempotentes() throws Exception {
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID"));
        try (var pool=Executors.newFixedThreadPool(4)) {
            var tarefas=new ArrayList<Future<?>>();
            for(int i=0;i<4;i++) tarefas.add(pool.submit(this::entregar));
            for(var t:tarefas)t.get(20,TimeUnit.SECONDS);
        }
        assertEquals(1,eventos.count()); assertTrue(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
    }
    @Test void notificacaoTransacionalAntesDoVinculoPedeReentrega() {
        byte[] bytes=("""
                {"id":"ORDE_teste","reference_id":"%s","charges":[{
                "id":"CHAR_teste","status":"PAID","amount":{"value":12990,"currency":"BRL"},
                "payment_method":{"type":"PIX"},"paid_at":"2026-10-19T12:00:00Z"}]}
                """).formatted(referencia).getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class,()->confirmacao.processarNotificacao(bytes,assinatura(bytes)));
        assertEquals(0,eventos.count());
        registros.registrarCheckout(referencia,"CHEC_teste","https://pagamento.sandbox.pagbank.com.br/pagamento?code=teste",Instant.now().plusSeconds(60));
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID"));
        confirmacao.processarNotificacao(bytes,assinatura(bytes));
        confirmacao.processarNotificacao(bytes,assinatura(bytes));
        assertEquals(1,eventos.count());assertTrue(mongo.findById(matricula.getId(),Matricula.class).isAcessoLiberado());
    }
    @Test void cancelamentoConcorrenteNuncaReativaMatricula() throws Exception {
        var factory=new MongoRepositoryFactory(mongo);
        var sessao=mock(SessaoService.class);
        var aluno=new Usuario();aluno.setId(matricula.getAlunoId());when(sessao.exigirUsuarioLogado()).thenReturn(aluno);
        var cursos=mock(CursoRepository.class);
        var matriculas=new MatriculaService(factory.getRepository(MatriculaRepository.class),cursos,sessao,mock(UsuarioRepository.class));
        when(gateway.consultar("CHEC_teste")).thenReturn(consulta("INACTIVE","PAID"));
        try(var pool=Executors.newFixedThreadPool(2)) {
            var cancelar=pool.submit(()->matriculas.cancelar(matricula.getId()));
            var aprovar=pool.submit(this::entregar);
            cancelar.get(20,TimeUnit.SECONDS);aprovar.get(20,TimeUnit.SECONDS);
        }
        entregar();
        var atual=mongo.findById(matricula.getId(),Matricula.class);
        assertEquals(StatusMatricula.CANCELADA,atual.getStatusMatricula());assertFalse(atual.isAcessoLiberado());
    }
}
