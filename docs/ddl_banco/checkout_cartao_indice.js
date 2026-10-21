if (!db.getCollectionNames().includes("pagamentos")) {
  throw new Error("A coleção pagamentos não existe. Prepare o schema antes desta migração.");
}
db.pagamentos.createIndex({ matricula_id: 1 }, {
  unique: true,
  partialFilterExpression: { status_atual: "pendente" },
  name: "uniq_pagamento_pendente_matricula"
});
print("Índice de pagamento pendente por matrícula configurado.");
