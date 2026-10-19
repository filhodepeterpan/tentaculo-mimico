// Executar no banco alvo já selecionado, antes da aplicação. Não apaga nem converte dados financeiros.
if (db.pagamentos.countDocuments({ versao: { $exists: false } }) !== 0) {
  throw new Error("Há pagamentos legados. Concilie e migre explicitamente esses registros antes de continuar.");
}
const validator = { $jsonSchema: {
  bsonType: "object",
  required: ["matricula_id", "valor", "status_atual", "mes_referencia", "data_vencimento", "criado_em", "versao", "tentativas"],
  properties: {
    matricula_id: { bsonType: "objectId" },
    valor: { bsonType: "decimal" },
    versao: { bsonType: ["long", "int"] },
    status_atual: { enum: ["PENDENTE", "APROVADO"] },
    mes_referencia: { bsonType: "date" },
    data_vencimento: { bsonType: "date" },
    criado_em: { bsonType: "date" },
    tentativas: { bsonType: "array", items: {
      bsonType: "object", required: ["reference_id", "status", "criado_em"],
      properties: {
        reference_id: { bsonType: "string" }, checkout_id: { bsonType: "string" },
        status: { enum: ["CRIANDO", "INCERTA", "WAITING", "IN_ANALYSIS", "PAID", "DECLINED", "CANCELED", "EXPIRED"] },
        criado_em: { bsonType: "date" }, expira_em: { bsonType: "date" }
      }
    } }
  }
} };
if (db.getCollectionNames().includes("pagamentos")) {
  const result = db.runCommand({ collMod: "pagamentos", validator });
  if (!result.ok) throw new Error("Falha ao atualizar validator de pagamentos.");
} else db.createCollection("pagamentos", { validator });
db.pagamentos.createIndex({ matricula_id: 1 }, { unique: true, name: "uniq_matricula_inicial" });
db.pagamentos.createIndex({ "tentativas.reference_id": 1 }, { unique: true, name: "uniq_referencia_matricula" });
db.pagamentos.createIndex({ "tentativas.checkout_id": 1 }, { unique: true, sparse: true, name: "uniq_checkout_matricula" });
db.pagamentos.createIndex({ "tentativas.transacao_externa_id": 1 }, { unique: true, sparse: true, name: "uniq_transacao_matricula" });
db.pagamentos.createIndex({ "tentativas.transacoes.transacao_externa_id": 1 }, { unique: true, sparse: true, name: "uniq_historico_transacao" });
db.webhooks_processados.createIndex({ evento_externo_id: 1 }, { unique: true, name: "uniq_evento" });
db.webhooks_processados.createIndex({ processado_em: 1 }, { expireAfterSeconds: 2592000, name: "ttl_processado_em" });
