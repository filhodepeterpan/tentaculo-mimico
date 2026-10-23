package br.com.tentaculomimico.client;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@EnabledIfSystemProperty(named = "checkout.ambiente.smoke", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(properties = "logging.level.org.mongodb.driver=WARN")
class AmbienteCheckoutSmokeTest {
    @Autowired MongoTemplate mongo;
    @Autowired PagBankClient client;
    @Autowired PagBankProperties config;

    @Test
    void indiceUnicoParcialEstaAplicadoNaBaseAutorizada() {
        List<Document> indices = mongo.getCollection("pagamentos").listIndexes().into(new ArrayList<>());
        Document indice = indices.stream().filter(i -> "uniq_pagamento_pendente_matricula".equals(i.getString("name")))
                .findFirst().orElseThrow();
        assertEquals(true, indice.getBoolean("unique"));
        assertEquals(new Document("status_atual", "pendente"), indice.get("partialFilterExpression"));
        System.out.println("MONGO_CHECKOUT: índice único parcial verificado na base " + mongo.getDb().getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREDIT_CARD", "DEBIT_CARD"})
    void criaLinkSandboxSemEfetuarCobrancaNoCartao(String metodo) {
        assertEquals(PagBankProperties.Environment.sandbox, config.environment(), "Teste permitido somente em sandbox");
        String retorno = config.publicBaseUrl() + "/pagamentos/teste-integracao";
        CheckoutRequest pedido = new CheckoutRequest("integracao-" + UUID.randomUUID(),
                OffsetDateTime.now().plusMinutes(30), true,
                List.of(new CheckoutRequest.Item("Teste de integração sem pagamento", 1, 100)),
                retorno, retorno, List.of(), List.of(new CheckoutRequest.Metodo(metodo)));
        URI link = client.criar(pedido);
        assertEquals("https", link.getScheme());
        System.out.println("PAGBANK_SANDBOX: checkout criado para " + metodo + "; nenhum cartão informado ou pagamento efetuado");
    }
}
