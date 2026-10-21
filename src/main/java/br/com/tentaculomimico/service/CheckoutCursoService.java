package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
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

/** Cria uma cobrança no checkout hospedado: nenhum dado sensível do cartão passa pelo servidor. */
@Service
public class CheckoutCursoService {
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

    public URI iniciar(String matriculaId, String metodo, String valorInformado) {
        if (!"credito".equals(metodo) && !"debito".equals(metodo))
            throw new IllegalArgumentException("Selecione crédito ou débito.");
        Matricula matricula = matriculas.buscarMatriculaDoAlunoLogado(matriculaId);
        if (matricula.getStatusMatricula() != StatusMatricula.AGUARDANDO_PAGAMENTO)
            throw new IllegalStateException("Esta matrícula não está aguardando pagamento.");
        Curso curso = cursos.findById(matricula.getCursoId())
                .orElseThrow(() -> new IllegalArgumentException("Curso não encontrado."));
        BigDecimal preco = curso.getPreco();
        if (preco == null || preco.signum() <= 0 || preco.scale() > 2)
            throw new IllegalStateException("Preço do curso inválido.");
        if (valorInformado != null && !valorInformado.isBlank()) {
            try {
                if (new BigDecimal(valorInformado).compareTo(preco) != 0)
                    throw new IllegalArgumentException("O valor informado difere do preço do curso.");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor informado inválido.");
            }
        }
        int centavos = preco.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        String referencia = "curso-" + UUID.randomUUID();
        Instant agora = Instant.now();
        Document registro = new Document("matricula_id", new ObjectId(matriculaId))
                .append("valor", new org.bson.types.Decimal128(preco))
                .append("status_atual", "pendente")
                .append("metodo_pagamento", "cartao")
                .append("mes_referencia", Date.from(LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()))
                .append("data_vencimento", Date.from(agora.plusSeconds(1800)))
                .append("criado_em", Date.from(agora))
                .append("tentativas", List.of(new Document("transacao_externa_id", referencia)
                        .append("status", "iniciada").append("criado_em", Date.from(agora))));
        try {
            mongo.getCollection("pagamentos").insertOne(registro);
        } catch (com.mongodb.MongoWriteException e) {
            if (e.getError().getCode() == 11000)
                throw new IllegalStateException("Já existe um pagamento pendente para esta matrícula.");
            throw e;
        }
        try {
            String retorno = config.publicBaseUrl();
            if (retorno == null || retorno.isBlank()) throw new IllegalStateException("Configure APP_PUBLIC_BASE_URL.");
            URI base = URI.create(retorno);
            if (!("https".equals(base.getScheme()) || (config.environment() == PagBankProperties.Environment.sandbox
                    && "http".equals(base.getScheme()) && "localhost".equals(base.getHost()))))
                throw new IllegalStateException("URL pública inválida.");
            String url = retorno.replaceAll("/$", "") + "/pagamentos/" + matriculaId;
            CheckoutRequest pedido = new CheckoutRequest(referencia, OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30),
                    false, List.of(new CheckoutRequest.Item(curso.getNome(), 1, centavos)),
                    url, url, List.of(new CheckoutRequest.MetodoConfig("credito".equals(metodo) ? "CREDIT_CARD" : "DEBIT_CARD", List.of())));
            URI checkout = pagbank.criar(pedido);
            mongo.getCollection("pagamentos").updateOne(new Document("_id", registro.getObjectId("_id")),
                    new Document("$set", new Document("tentativas.0.status", "checkout_criado")));
            return checkout;
        } catch (RuntimeException e) {
            mongo.getCollection("pagamentos").updateOne(new Document("_id", registro.getObjectId("_id")),
                    new Document("$set", new Document("status_atual", "recusado")
                            .append("tentativas.0.status", "falha_criacao_checkout")));
            if (e instanceof IllegalStateException) throw e;
            throw new IllegalStateException("Não foi possível iniciar o pagamento. Tente novamente.");
        }
    }
}
