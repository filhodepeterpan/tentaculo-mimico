print('Criando administrador de teste...')

db.usuarios.insertOne({
  _id: ObjectId('6ac33b883aad226be7aafcd1'),
  nome: 'Admin Teste',
  cpf: '00000000000',
  email: 'admin@teste.com',
  tipo_usuario: 'ADMINISTRADOR',
  autenticacao: {
    senha_hash: '$2a$10$jxhgFJrM0hME78KyvgnL/ugUBDy/hNHxpl.M2O1r1MVj5ZLgOVS9m', // SenhaDeTeste123
    provedor: 'LOCAL'
  },
  tentativas_login_falhas: NumberInt('0'),
  _class: 'br.com.tentaculomimico.model.Usuario'
});

print('Administrador de teste criado com sucesso!');