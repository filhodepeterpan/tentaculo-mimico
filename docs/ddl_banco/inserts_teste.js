/*
 * INSERTS DE TESTE - TENTÁCULO MÍMICO
 *
 * rodar no terminal:
 * mongosh "connection-string-do-atlas" inserts_teste.js
 *
 * cria:
 * - 3 alunos
 * - 3 professores
 * - 6 cursos (2 para cada professor)
 *
 * Todos os usuários usam autenticação local.
 * As imagens ficam como null.
 * mongosh "mongodb+srv://cluster-tentaculomimico.d8n5pee.mongodb.net/tm_douglas" --apiVersion 1 --username tm_douglas --password tm_douglas inserts_teste.js
 */

db.usuarios.drop();
db.cursos.drop();

print("Iniciando inserts de teste...");

const agora = new Date();

// ============================================================
// USUÁRIOS
// ============================================================

// Hash BCrypt válido gerado para a senha '123456'
const senhaHashTeste =
  "$2a$10$Rkr3NoHQ4HbUfqtOzjSPn.TM.RJsiUBSUkxlgGPkqMqZC534uuXJm";

const alunos = [
  {
    _id: ObjectId(),
    tipo_usuario: "ALUNO",
    nome: "Aluno Teste 01",
    email: "aluno01@teste.com",
    cpf: "11111111101",
    data_nascimento: ISODate("2001-01-10"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
  {
    _id: ObjectId(),
    tipo_usuario: "ALUNO",
    nome: "Aluno Teste 02",
    email: "aluno02@teste.com",
    cpf: "22222222202",
    data_nascimento: ISODate("2002-02-20"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
  {
    _id: ObjectId(),
    tipo_usuario: "ALUNO",
    nome: "Aluno Teste 03",
    email: "aluno03@teste.com",
    cpf: "33333333303",
    data_nascimento: ISODate("2003-03-30"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
];

const professores = [
  {
    _id: ObjectId(),
    tipo_usuario: "PROFESSOR",
    nome: "Professor Teste 01",
    email: "professor01@teste.com",
    cpf: "44444444404",
    data_nascimento: ISODate("1985-04-15"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
  {
    _id: ObjectId(),
    tipo_usuario: "PROFESSOR",
    nome: "Professor Teste 02",
    email: "professor02@teste.com",
    cpf: "55555555505",
    data_nascimento: ISODate("1980-05-25"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
  {
    _id: ObjectId(),
    tipo_usuario: "PROFESSOR",
    nome: "Professor Teste 03",
    email: "professor03@teste.com",
    cpf: "66666666606",
    data_nascimento: ISODate("1978-06-05"),
    foto_perfil: null,
    autenticacao: {
      senha_hash: senhaHashTeste,
      provedor: "LOCAL",
      provedor_id: null,
    },
    email_confirmado: true,
    tentativas_login_falhas: 0,
    bloqueado_ate: null,
    ativo: true,
    criado_em: agora,
    atualizado_em: agora,
  },
];

db.usuarios.insertMany(alunos);
db.usuarios.insertMany(professores);

print("3 alunos inseridos.");
print("3 professores inseridos.");

// ============================================================
// CURSOS
// ============================================================

const cursos = [
  {
    _id: ObjectId(),
    professor_id: professores[0]._id,
    professor_nome: professores[0].nome,
    nome: "Java para Iniciantes",
    descricao:
      "Curso introdutório de programação orientada a objetos com Java.",
    imagem_capa: null,
    carga_horaria: 40,
    horarios: [
      {
        dia_semana: 2,
        hora_inicio: "19:00",
        hora_fim: "21:00",
      },
      {
        dia_semana: 4,
        hora_inicio: "19:00",
        hora_fim: "21:00",
      },
    ],
    preco: NumberDecimal("149.90"),
    vagas_totais: 30,
    vagas_disponiveis: 30,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-03"),
    data_fim: ISODate("2026-12-03"),
    data_cadastro: agora,
    ativo: true,
  },

  {
    _id: ObjectId(),
    professor_id: professores[0]._id,
    professor_nome: professores[0].nome,
    nome: "Spring Boot na Prática",
    descricao: "Desenvolvimento de APIs e aplicações web com Spring Boot.",
    imagem_capa: null,
    carga_horaria: 50,
    horarios: [
      {
        dia_semana: 3,
        hora_inicio: "19:00",
        hora_fim: "21:30",
      },
      {
        dia_semana: 5,
        hora_inicio: "19:00",
        hora_fim: "21:30",
      },
    ],
    preco: NumberDecimal("199.90"),
    vagas_totais: 25,
    vagas_disponiveis: 25,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-04"),
    data_fim: ISODate("2026-12-11"),
    data_cadastro: agora,
    ativo: true,
  },

  {
    _id: ObjectId(),
    professor_id: professores[1]._id,
    professor_nome: professores[1].nome,
    nome: "Banco de Dados Relacional",
    descricao: "Fundamentos de modelagem, SQL e bancos de dados relacionais.",
    imagem_capa: null,
    carga_horaria: 40,
    horarios: [
      {
        dia_semana: 2,
        hora_inicio: "18:30",
        hora_fim: "20:30",
      },
      {
        dia_semana: 4,
        hora_inicio: "18:30",
        hora_fim: "20:30",
      },
    ],
    preco: NumberDecimal("129.90"),
    vagas_totais: 35,
    vagas_disponiveis: 35,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-09"),
    data_fim: ISODate("2026-12-09"),
    data_cadastro: agora,
    ativo: true,
  },

  {
    _id: ObjectId(),
    professor_id: professores[1]._id,
    professor_nome: professores[1].nome,
    nome: "MongoDB e NoSQL",
    descricao: "Introdução a bancos de dados NoSQL usando MongoDB.",
    imagem_capa: null,
    carga_horaria: 30,
    horarios: [
      {
        dia_semana: 6,
        hora_inicio: "09:00",
        hora_fim: "12:00",
      },
    ],
    preco: NumberDecimal("119.90"),
    vagas_totais: 20,
    vagas_disponiveis: 20,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-14"),
    data_fim: ISODate("2026-12-12"),
    data_cadastro: agora,
    ativo: true,
  },

  {
    _id: ObjectId(),
    professor_id: professores[2]._id,
    professor_nome: professores[2].nome,
    nome: "JavaScript Moderno",
    descricao: "JavaScript moderno, manipulação do DOM, módulos e APIs.",
    imagem_capa: null,
    carga_horaria: 40,
    horarios: [
      {
        dia_semana: 3,
        hora_inicio: "18:30",
        hora_fim: "20:30",
      },
      {
        dia_semana: 5,
        hora_inicio: "18:30",
        hora_fim: "20:30",
      },
    ],
    preco: NumberDecimal("139.90"),
    vagas_totais: 30,
    vagas_disponiveis: 30,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-10"),
    data_fim: ISODate("2026-12-10"),
    data_cadastro: agora,
    ativo: true,
  },

  {
    _id: ObjectId(),
    professor_id: professores[2]._id,
    professor_nome: professores[2].nome,
    nome: "Desenvolvimento Web com TypeScript",
    descricao: "Desenvolvimento web utilizando TypeScript e boas práticas.",
    imagem_capa: null,
    carga_horaria: 45,
    horarios: [
      {
        dia_semana: 6,
        hora_inicio: "14:00",
        hora_fim: "17:00",
      },
    ],
    preco: NumberDecimal("159.90"),
    vagas_totais: 25,
    vagas_disponiveis: 25,
    status: "DISPONIVEL",
    data_inicio: ISODate("2026-11-14"),
    data_fim: ISODate("2026-12-19"),
    data_cadastro: agora,
    ativo: true,
  },
];

db.cursos.insertMany(cursos);

print("6 cursos inseridos.");
print("2 cursos vinculados ao Professor Teste 01.");
print("2 cursos vinculados ao Professor Teste 02.");
print("2 cursos vinculados ao Professor Teste 03.");

print("Inserts de teste concluídos com sucesso!");
