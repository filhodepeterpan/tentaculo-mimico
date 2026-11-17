package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Cria (ou reaproveita) o checkout hospedado do PagBank para o primeiro pagamento de uma matrícula.
 * Nenhum dado de cartão passa pelo servidor. Quem confirma o pagamento depois é o PagamentoCursoService.
 *
 * Todas as formas de pagamento habilitadas na conta PagBank (Pix, crédito com parcelamento, débito)
 * ficam disponíveis: não fixamos payment_methods nem INSTALLMENTS_LIMIT.
 */
@Service
public class CheckoutCursoService {
    private static final long VALIDADE_CHECKOUT_HORAS = 2;
    private static final int LIMITE_URL_NOTIFICACAO = 100; // limite do PagBank para notification_urls
    private static final String CAMINHO_WEBHOOK = "/webhooks/pagbank";

    private final MatriculaService matriculas;
    private final CursoRepository cursos;
    private final MongoTemplate mongo;
    private final PagBankClient pagbank;
    private final PagBankProperties config;

    public CheckoutCursoService(MatriculaService matriculas, CursoRepository cursos, MongoTemplate mongo,
                                PagBankClient pagbank, PagBankProperties config) {
        this.matriculas = matriculas;
        this.cursos = cursos;
        this.mongo = mongo;
        this.pagbank = pagbank;
        this.config = config;
    }

    /** Devolve o link do checkout do PagBank. Clique duplo: reaproveita o checkout ainda válido. */
    public URI iniciar(String matriculaId) {
        Matricula matricula = matriculas.buscarMatriculaDoAlunoLogado(matriculaId);
        if (matricula.getStatusMatricula() != StatusMatricula.AGUARDANDO_PAGAMENTO)
            throw new IllegalStateException("Esta matrícula não está aguardando pagamento.");
        Curso curso = cursos.findById(matricula.getCursoId())
                .orElseThrow(() -> new IllegalArgumentException("Curso não encontrado."));
        BigDecimal preco = curso.getPreco();
        if (preco == null || preco.signum() <= 0 || preco.scale() > 2)
            throw new IllegalStateException("Preço do curso inválido.");
        // O valor vem sempre do curso no banco, nunca do navegador.
        int centavos = preco.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();

        ObjectId matriculaOid = new ObjectId(matriculaId);
        MongoCollection<Document> pagamentos = mongo.getCollection("pagamentos");
        Instant agora = Instant.now();

        Document aberto = pagamentoPendente(pagamentos, matriculaOid);
        if (aberto != null) {
            URI existente = checkoutReaproveitavel(aberto, agora);
            if (existente != null) return existente;
            Date criado = aberto.getDate("criado_em");
            if (aberto.getString("checkout_url") == null && criado != null
                    && criado.toInstant().isAfter(agora.minusSeconds(60)))
                throw new IllegalStateException("Já existe um pagamento sendo preparado. Aguarde alguns segundos e tente novamente.");
            // Checkout expirado: encerra o pendente para liberar um novo.
            pagamentos.updateMany(Filters.and(Filters.eq("matricula_id", matriculaOid),
                            Filters.eq("status_atual", "pendente")),
                    Updates.set("status_atual", "vencido"));
        }

        String referencia = "curso-" + UUID.randomUUID();
        OffsetDateTime expira = OffsetDateTime.now(ZoneOffset.UTC).plusHours(VALIDADE_CHECKOUT_HORAS);
        Document registro = new Document("matricula_id", matriculaOid)
                .append("valor", new Decimal128(preco))
                .append("status_atual", "pendente")
                .append("metodo_pagamento", null) // só se sabe depois, quando o PagBank confirmar
                .append("mes_referencia", Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .append("data_vencimento", Date.from(expira.toInstant()))
                .append("criado_em", Date.from(agora))
                .append("tentativas", List.of(new Document("transacao_externa_id", referencia)
                        .append("status", "iniciada").append("criado_em", Date.from(agora))));
        try {
            pagamentos.insertOne(registro);
        } catch (com.mongodb.MongoWriteException e) {
            if (e.getError().getCode() == 11000) {
                // Outro clique criou o pagamento pendente no mesmo instante.
                Document concorrente = pagamentoPendente(pagamentos, matriculaOid);
                URI existente = concorrente != null ? checkoutReaproveitavel(concorrente, agora) : null;
                if (existente != null) return existente;
                throw new IllegalStateException("Já existe um pagamento sendo preparado. Aguarde alguns segundos e tente novamente.");
            }
            throw e;
        }

        try {
            String base = baseUrlPublica();
            String retorno = base + "/pagamentos/" + matriculaId + "/retorno";
            // O PagBank só entrega webhook em URL pública HTTPS. No localhost o retorno do checkout cobre.
            String webhook = base + CAMINHO_WEBHOOK;
            List<String> notificacoes = base.startsWith("https://") && webhook.length() <= LIMITE_URL_NOTIFICACAO
                    ? List.of(webhook) : List.of();
            String nomeItem = curso.getNome().length() > 64 ? curso.getNome().substring(0, 64) : curso.getNome();
            CheckoutRequest pedido = new CheckoutRequest(referencia, expira, true,
                    List.of(new CheckoutRequest.Item(nomeItem, 1, centavos)),
                    retorno, retorno, List.of(), null, notificacoes, notificacoes);

            PagBankClient.CheckoutAberto checkout = pagbank.criarCompleto(pedido);

            Document set = new Document("checkout_url", checkout.pay().toString())
                    .append("checkout_expira_em", Date.from(expira.toInstant()))
                    .append("tentativas.0.status", "checkout_criado");
            if (checkout.id() != null) set.append("checkout_id", checkout.id());
            pagamentos.updateOne(Filters.eq("_id", registro.getObjectId("_id")), new Document("$set", set));
            return checkout.pay();
        } catch (RuntimeException e) {
            pagamentos.updateOne(Filters.eq("_id", registro.getObjectId("_id")),
                    new Document("$set", new Document("status_atual", "recusado")
                            .append("tentativas.0.status", "falha_criacao_checkout")));
            if (e instanceof IllegalStateException || e instanceof PagBankClient.CheckoutIndisponivelException) throw e;
            throw new IllegalStateException("Não foi possível iniciar o pagamento. Tente novamente.");
        }
    }

    private Document pagamentoPendente(MongoCollection<Document> pagamentos, ObjectId matriculaOid) {
        return pagamentos.find(Filters.and(Filters.eq("matricula_id", matriculaOid),
                        Filters.eq("status_atual", "pendente")))
                .sort(Sorts.descending("criado_em")).first();
    }

    /** Link do checkout já criado, se ainda tem pelo menos 2 minutos de validade; senão null. */
    private URI checkoutReaproveitavel(Document pagamento, Instant agora) {
        String url = pagamento.getString("checkout_url");
        Date expira = pagamento.getDate("checkout_expira_em");
        if (url == null || expira == null || !expira.toInstant().isAfter(agora.plusSeconds(120))) return null;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) ? uri : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String baseUrlPublica() {
        String base = config.publicBaseUrl();
        if (base == null || base.isBlank()) throw new IllegalStateException("Configure APP_PUBLIC_BASE_URL.");
        URI uri;
        try {
            uri = URI.create(base.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("URL pública inválida.");
        }
        boolean https = "https".equals(uri.getScheme());
        boolean localSandbox = config.environment() == PagBankProperties.Environment.sandbox
                && "http".equals(uri.getScheme()) && "localhost".equals(uri.getHost());
        if (uri.getHost() == null || !(https || localSandbox)) throw new IllegalStateException("URL pública inválida.");
        return base.strip().replaceAll("/+$", "");
    }
}
