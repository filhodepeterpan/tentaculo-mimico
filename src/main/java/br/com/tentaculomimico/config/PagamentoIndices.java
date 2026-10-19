package br.com.tentaculomimico.config;

import br.com.tentaculomimico.model.PagamentoMatricula;
import br.com.tentaculomimico.model.WebhookProcessado;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;
import java.time.Duration;

/** Falha no startup se não for possível garantir a unicidade das cobranças. */
@Component
public class PagamentoIndices implements InitializingBean {
    private final MongoTemplate mongo;
    public PagamentoIndices(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public void afterPropertiesSet() {
        var pagamentos = mongo.indexOps(PagamentoMatricula.class);
        pagamentos.ensureIndex(new Index().on("matricula_id", Direction.ASC).unique().named("uniq_matricula_inicial"));
        pagamentos.ensureIndex(new Index().on("tentativas.reference_id", Direction.ASC).unique().named("uniq_referencia_matricula"));
        pagamentos.ensureIndex(new Index().on("tentativas.checkout_id", Direction.ASC).unique().sparse().named("uniq_checkout_matricula"));
        pagamentos.ensureIndex(new Index().on("tentativas.transacao_externa_id", Direction.ASC).unique().sparse().named("uniq_transacao_matricula"));
        pagamentos.ensureIndex(new Index().on("tentativas.transacoes.transacao_externa_id", Direction.ASC).unique().sparse().named("uniq_historico_transacao"));
        var eventos = mongo.indexOps(WebhookProcessado.class);
        eventos.ensureIndex(new Index().on("evento_externo_id", Direction.ASC).unique().named("uniq_evento"));
        eventos.ensureIndex(new Index().on("processado_em", Direction.ASC).expire(Duration.ofDays(30)).named("ttl_processado_em"));
    }
}
