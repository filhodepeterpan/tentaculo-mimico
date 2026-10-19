/*
SETUP DO DATABASE (MongoDB)

rodar no terminal:
mongosh "connection-string-do-atlas" setup_database.js

a connection string do atlas vai ser colocada no drive Projeto Integrador III > Desenvolvimento > Banco de Dados > Script > connection_string.gdoc
usuário e senha do atlas vão ser colocados no drive Projeto Integrador III > Desenvolvimento > Banco de Dados > Script > credenciais_db.gdoc
*/

print("Criando coleção: usuarios");
db.createCollection("usuarios", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: [
        "tipo_usuario", "nome", "email", "cpf", "data_nascimento",
        "autenticacao", "email_confirmado", "tentativas_login_falhas",
        "ativo", "criado_em", "atualizado_em"
      ],
      properties: {
        tipo_usuario: { enum: ["aluno", "professor", "administrador"] },
        nome: { bsonType: "string" },
        email: { bsonType: "string" },
        cpf: { bsonType: "string" },
        data_nascimento: { bsonType: "date" },
        foto_perfil: { bsonType: ["string", "null"] },
        autenticacao: {
          bsonType: "object",
          required: ["provedor"],
          properties: {
            senha_hash: { bsonType: ["string", "null"] },
            provedor: { enum: ["local", "google"] },
            provedor_id: { bsonType: ["string", "null"] }
          }
        },
        email_confirmado: { bsonType: "bool" },
        tentativas_login_falhas: { bsonType: "int" },
        bloqueado_ate: { bsonType: ["date", "null"] },
        ativo: { bsonType: "bool" },
        criado_em: { bsonType: "date" },
        atualizado_em: { bsonType: "date" }
      }
    }
  }
});

db.usuarios.createIndex({ email: 1 }, { unique: true, name: "uniq_email" });
db.usuarios.createIndex({ cpf: 1 }, { unique: true, name: "uniq_cpf" });
db.usuarios.createIndex(
  { "autenticacao.provedor": 1, "autenticacao.provedor_id": 1 },
  {
    unique: true,
    partialFilterExpression: { "autenticacao.provedor_id": { $type: "string" } },
    name: "uniq_provedor_id"
  }
);

print("Criando coleção: cursos");
db.createCollection("cursos", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: [
        "professor_id", "professor_nome", "nome", "descricao",
        "carga_horaria", "horarios", "preco", "vagas_totais",
        "vagas_disponiveis", "status", "data_inicio", "data_cadastro", "ativo"
      ],
      properties: {
        professor_id: { bsonType: "objectId" },
        professor_nome: { bsonType: "string" },
        nome: { bsonType: "string" },
        descricao: { bsonType: "string" },
        imagem_capa: { bsonType: ["string", "null"] },
        carga_horaria: { bsonType: "int" },
        horarios: {
          bsonType: "array",
          items: {
            bsonType: "object",
            required: ["dia_semana", "hora_inicio", "hora_fim"],
            properties: {
              dia_semana: { bsonType: "int", minimum: 1, maximum: 7 },
              hora_inicio: { bsonType: "string" }, // formato "HH:mm"
              hora_fim: { bsonType: "string" }
            }
          }
        },
        preco: { bsonType: "decimal" },
        vagas_totais: { bsonType: "int" },
        vagas_disponiveis: { bsonType: "int" },
        status: { enum: ["rascunho", "disponivel", "indisponivel"] },
        data_inicio: { bsonType: "date" },
        data_fim: { bsonType: ["date", "null"] },
        data_cadastro: { bsonType: "date" },
        ativo: { bsonType: "bool" }
      }
    }
  }
});

db.cursos.createIndex({ professor_id: 1 });
db.cursos.createIndex({ status: 1 });


print("Criando coleção: matriculas");
db.createCollection("matriculas", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["aluno_id", "curso_id", "status", "acesso_liberado", "data_solicitacao"],
      properties: {
        aluno_id: { bsonType: "objectId" },
        curso_id: { bsonType: "objectId" },
        status: {
          enum: ["pendente", "aceita_aguardando_pagamento", "ativa", "recusada", "cancelada"]
        },
        acesso_liberado: { bsonType: "bool" },
        data_solicitacao: { bsonType: "date" },
        data_resposta_professor: { bsonType: ["date", "null"] },
        data_cancelamento: { bsonType: ["date", "null"] }
      }
    }
  }
});

db.matriculas.createIndex({ aluno_id: 1 });
db.matriculas.createIndex({ curso_id: 1 });
db.matriculas.createIndex(
  { aluno_id: 1, curso_id: 1 },
  {
    unique: true,
    partialFilterExpression: {
      status: { $in: ["pendente", "aceita_aguardando_pagamento", "ativa"] }
    },
    name: "uniq_matricula_em_andamento"
  }
);

print("Criando coleção: pagamentos");
db.createCollection("pagamentos", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["matricula_id", "valor", "status_atual", "mes_referencia", "data_vencimento", "criado_em"],
      properties: {
        matricula_id: { bsonType: "objectId" },
        valor: { bsonType: "decimal" },
        status_atual: { enum: ["pendente", "aprovado", "recusado", "vencido", "estornado"] },
        metodo_pagamento: { bsonType: ["string", "null"], enum: ["pix", "cartao", null] },
        mes_referencia: { bsonType: "date" },
        data_vencimento: { bsonType: "date" },
        data_pagamento: { bsonType: ["date", "null"] },
        // guardando só a referência (transacao_externa_id), SEM DADOS DO CARTÃO, pra evitar problemas de LGPD.
        tentativas: {
          bsonType: "array",
          items: {
            bsonType: "object",
            required: ["transacao_externa_id", "status", "criado_em"],
            properties: {
              transacao_externa_id: { bsonType: "string" },
              status: { bsonType: "string" },
              criado_em: { bsonType: "date" }
            }
          }
        },
        criado_em: { bsonType: "date" }
      }
    }
  }
});

db.pagamentos.createIndex({ matricula_id: 1 });
db.pagamentos.createIndex({ status_atual: 1, data_vencimento: 1 });
db.pagamentos.createIndex({ "tentativas.transacao_externa_id": 1 });

print("Criando coleção: webhooks_processados");
db.createCollection("webhooks_processados", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["evento_externo_id", "processado_em"],
      properties: {
        evento_externo_id: { bsonType: "string" },
        processado_em: { bsonType: "date" }
      }
    }
  }
});

db.webhooks_processados.createIndex({ evento_externo_id: 1 }, { unique: true, name: "uniq_evento" });
// apaga depois de 30 dias
db.webhooks_processados.createIndex(
  { processado_em: 1 },
  { expireAfterSeconds: 2592000, name: "ttl_processado_em" }
);

print("Banco pronto. Coleções criadas: usuarios, cursos, matriculas, pagamentos, webhooks_processados");
