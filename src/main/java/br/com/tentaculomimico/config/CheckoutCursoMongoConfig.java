package br.com.tentaculomimico.config;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class CheckoutCursoMongoConfig {
    @Bean
    @ConditionalOnProperty(name = "app.pagamento.criar-indice", havingValue = "true")
    ApplicationRunner criarIndicePagamentoPendente(MongoTemplate mongo) {
        return args -> mongo.getCollection("pagamentos").createIndex(
                new Document("matricula_id", 1),
                new IndexOptions().unique(true).name("uniq_pagamento_pendente_matricula")
                        .partialFilterExpression(new Document("status_atual", "pendente")));
    }
}
