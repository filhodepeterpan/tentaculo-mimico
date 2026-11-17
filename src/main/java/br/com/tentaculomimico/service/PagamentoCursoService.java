package br.com.tentaculomimico.service;

import br.com.tentaculomimico.client.PagBankClient;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Confirma o pagamento da matrícula. Existem dois caminhos, e os dois terminam em
 * {@link #aprovarSePago}:
 *
 *  1. Webhook do PagBank ({@link #processarNotificacao}): só serve de aviso. O corpo recebido NUNCA é
 *     confiado: o pedido é consultado de novo na API do PagBank com o nosso token.
 *  2. Retorno do aluno ao site ({@link #reconciliar}): consulta o checkout na API do PagBank. Cobre o
 *     localhost (onde o webhook não chega) e qualquer webhook perdido.
 *
 * É tudo idempotente: pode rodar várias vezes para o mesmo pagamento sem efeito colateral.
 */
@Service
public class PagamentoCursoService {
    private static final Logger LOG = LoggerFactory.getLogger(PagamentoCursoService.class);
    private static final JsonMapper JSON = new JsonMapper();
    private static final Pattern ID_PEDIDO = Pattern.compile("ORDE_[A-Za-z0-9-]{1,64}");

    private final MongoTemplate mongo;
    private final PagBankClient pagbank;
    private final MatriculaService matriculas;

    public PagamentoCursoService(MongoTemplate mongo, PagBankClient pagbank, MatriculaService matriculas) {
        this.mongo = mongo;
        this.pagbank = pagbank;
        this.matriculas = matriculas;
    }

    // =========================================================================
    // Webhook
    // =========================================================================

    /**
     * @throws IllegalArgumentException corpo que não é JSON
     * @throws PagBankClient.ConsultaIndisponivelException não deu para conferir no PagBank agora
     *         (o controller responde erro para o PagBank reenviar a notificação mais tarde)
     */
    public void processarNotificacao(String corpo) {
        JsonNode notificacao;
        try {
            notificacao = JSON.readTree(corpo);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Notificação inválida");
        }
        String id = notificacao.path("id").asText("");

        if (id.startsWith("CHEC_")) {
            // Evento do checkout. Só nos interessa a expiração (o pagamento chega como evento de pedido).
            if ("EXPIRED".equals(notificacao.path("status").asText(""))) {
                pagamentos().updateMany(Filters.and(Filters.eq("checkout_id", id),
                                Filters.eq("status_atual", "pendente")),
                        Updates.set("status_atual", "vencido"));
            }
            return;
        }
        if (!ID_PEDIDO.matcher(id).matches()) return; // evento que não é de pagamento

        String chaveEvento = id + ":" + impressaoDosPagamentos(notificacao);
        MongoCollection<Document> processados = mongo.getCollection("webhooks_processados");
        if (processados.countDocuments(Filters.eq("evento_externo_id", chaveEvento)) > 0) return;

        // Fonte de verdade: o pedido na API do PagBank, não o que veio no corpo da requisição.
        JsonNode pedido = pagbank.consultarPedido(id);
        String referencia = pedido.path("reference_id").asText("");
        Document pagamento = referencia.isEmpty() ? null
                : pagamentos().find(Filters.eq("tentativas.transacao_externa_id", referencia)).first();
        if (pagamento == null) {
            LOG.info("Notificação do PagBank sem pagamento correspondente (pedido {}, referência '{}')", id, referencia);
        } else {
            aprovarSePago(pagamento, pedido);
        }

        try {
            processados.insertOne(new Document("evento_externo_id", chaveEvento).append("processado_em", new Date()));
        } catch (MongoWriteException e) {
            if (e.getError().getCode() != 11000) throw e; // 11000: outra entrega já registrou, tudo certo
        }
    }

    /** Id + status de cada cobrança: um reenvio idêntico tem a mesma chave; uma mudança de status, não. */
    private String impressaoDosPagamentos(JsonNode pedido) {
        StringBuilder sb = new StringBuilder();
        JsonNode charges = pedido.path("charges");
        if (charges.isArray()) {
            for (JsonNode c : charges) {
                sb.append(c.path("id").asText("?")).append('=').append(c.path("status").asText("?")).append(';');
            }
        }
        return sb.length() > 200 ? sb.substring(0, 200) : sb.toString();
    }

    // =========================================================================
    // Retorno do aluno ao site
    // =========================================================================

    /**
     * Confere no PagBank se a matrícula já foi paga e, se foi, ativa. Nunca lança por falha do PagBank:
     * se não deu para conferir, a tela só diz que a confirmação ainda não chegou.
     */
    public void reconciliar(String matriculaId) {
        try {
            ObjectId matriculaOid = new ObjectId(matriculaId);

            // Aprovado mas matrícula ainda não ativada (falha no meio do caminho): termina o serviço.
            Document aprovado = pagamentos().find(Filters.and(Filters.eq("matricula_id", matriculaOid),
                    Filters.eq("status_atual", "aprovado"))).first();
            if (aprovado != null) {
                matriculas.ativarPorPagamentoAprovado(matriculaId);
                return;
            }

            // Ainda não pago: consulta o checkout mais recente (pendente ou vencido, que pode ter sido pago em cima da hora).
            Document pagamento = pagamentos().find(Filters.and(Filters.eq("matricula_id", matriculaOid),
                            Filters.in("status_atual", "pendente", "vencido"),
                            Filters.ne("checkout_id", null)))
                    .sort(Sorts.descending("criado_em")).first();
            if (pagamento == null) return;

            JsonNode checkout = pagbank.consultarCheckout(pagamento.getString("checkout_id"));
            List<JsonNode> pedidos = new ArrayList<>();
            Set<String> idsPedido = new LinkedHashSet<>();
            coletar(checkout, pedidos, idsPedido, 0);

            for (JsonNode pedido : pedidos) {
                if (aprovarSePago(pagamento, pedido)) return;
            }
            int consultas = 0;
            for (String idPedido : idsPedido) {
                if (++consultas > 10) break;
                if (aprovarSePago(pagamento, pagbank.consultarPedido(idPedido))) return;
            }
        } catch (PagBankClient.ConsultaIndisponivelException e) {
            LOG.info("Não foi possível conferir o pagamento da matrícula {} no PagBank agora", matriculaId);
        } catch (IllegalArgumentException e) {
            LOG.warn("Matrícula inválida ao conferir pagamento: {}", matriculaId);
        }
    }

    /**
     * A resposta de consulta do checkout não tem formato garantido na documentação pública. Em vez de
     * depender de um caminho fixo, procura em qualquer lugar: objetos com lista "charges" (já são o
     * pedido) e textos no formato ORDE_... (ids de pedido, que depois consultamos um a um).
     */
    private void coletar(JsonNode no, List<JsonNode> pedidos, Set<String> ids, int profundidade) {
        if (no == null || profundidade > 8) return;
        if (no.isObject() && no.path("charges").isArray()) pedidos.add(no);
        if (no.isObject() || no.isArray()) {
            for (JsonNode filho : no) coletar(filho, pedidos, ids, profundidade + 1);
        } else {
            String texto = no.asText("");
            if (ID_PEDIDO.matcher(texto).matches()) ids.add(texto);
        }
    }

    // =========================================================================
    // Aprovação (comum aos dois caminhos)
    // =========================================================================

    /**
     * Se o pedido tem cobrança PAID com valor pelo menos igual ao do pagamento, marca o pagamento como
     * aprovado e ativa a matrícula. Valor maior é aceito porque o parcelamento com juros repassado ao
     * comprador faz o PagBank cobrar mais que o preço do curso.
     */
    private boolean aprovarSePago(Document pagamento, JsonNode pedido) {
        long pago = 0;
        String chargeId = null;
        String tipo = null;
        JsonNode charges = pedido.path("charges");
        if (charges.isArray()) {
            for (JsonNode c : charges) {
                if (!"PAID".equals(c.path("status").asText(""))) continue;
                long valor = c.path("amount").path("value").longValue();
                if (valor <= 0) valor = c.path("amount").path("summary").path("paid").longValue();
                pago += valor;
                if (chargeId == null) {
                    chargeId = c.path("id").asText(null);
                    tipo = c.path("payment_method").path("type").asText(null);
                }
            }
        }
        if (pago <= 0) return false;

        Object valorBruto = pagamento.get("valor");
        long esperado = valorBruto instanceof Decimal128 d
                ? d.bigDecimalValue().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
                : Long.MAX_VALUE;
        if (pago < esperado) {
            LOG.warn("Pagamento {} com valor pago ({}) menor que o esperado ({}): não aprovado",
                    pagamento.getObjectId("_id"), pago, esperado);
            return false;
        }

        // Transição atômica: só uma execução (webhook ou retorno) aplica a mudança.
        Document tentativa = new Document("transacao_externa_id", chargeId != null ? chargeId : "pago")
                .append("status", "PAID").append("criado_em", new Date());
        pagamentos().updateOne(Filters.and(Filters.eq("_id", pagamento.getObjectId("_id")),
                        Filters.ne("status_atual", "aprovado")),
                Updates.combine(Updates.set("status_atual", "aprovado"),
                        Updates.set("data_pagamento", new Date()),
                        Updates.set("metodo_pagamento", "PIX".equals(tipo) ? "pix" : "cartao"),
                        Updates.push("tentativas", tentativa)));

        String matriculaId = pagamento.getObjectId("matricula_id").toHexString();
        boolean ativa = matriculas.ativarPorPagamentoAprovado(matriculaId);
        if (!ativa) {
            LOG.warn("Pagamento aprovado para a matrícula {}, mas ela não está mais aguardando pagamento", matriculaId);
        }
        return true;
    }

    private MongoCollection<Document> pagamentos() {
        return mongo.getCollection("pagamentos");
    }
}
