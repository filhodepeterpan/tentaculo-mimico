# Operação do pagamento inicial de matrícula

## Configuração

- `PAGBANK_MATRICULA_SANDBOX_TOKEN`: credencial **Sandbox** explícita, sem fallback para `PAGBANK_TOKEN` das doações.
- `MATRICULA_PUBLIC_BASE_URL`: HTTPS sem caminho/query; padrão de desenvolvimento solicitado:
  `https://subtentacular-unhorizontally-antoinette.ngrok-free.app`.
- As duas notificações usam `POST /webhooks/pagbank/matriculas`; o retorno é
  `GET /pagamentos/{matriculaId}`. Encaminhar o ngrok para a porta da aplicação.
- Únicas operações remotas: POST no endpoint fixo `https://sandbox.api.pagseguro.com/checkouts`
  e GET `/checkouts/{checkout_id}?limit=100` nesse mesmo host. Nunca seguir links SELF/Orders.
- PAY permitido: `https://pagamento.sandbox.pagbank.com.br/pagamento?code=...`.
  Ausência de link, estado desconhecido ou URL fora dessa regra falham fechado.
- Métodos solicitados: Pix e crédito em uma parcela, somente na página hospedada.
  Não há assinatura, recorrência, mensalidade automática ou coleta local de dados de cartão.

## Persistência e recuperação

Banco novo: `docs/ddl_banco/setup_database.js`. Banco existente: aplicar
`docs/ddl_banco/migrar_pagamento_inicial.js` antes de iniciar a versão nova.
O script não apaga dados; registros legados exigem conciliação explícita antes da migração.

Um índice único de matrícula e `@Version` garantem uma reserva vencedora. Só essa requisição
envia POST ao gateway. Demais requisições reutilizam o Checkout após consulta autenticada e
validação de URL/expiração. Falhas preservam a tentativa e a referência.

`INCERTA` (incluindo crash após reserva) bloqueia novas cobranças. Um webhook de Checkout
autenticado pode vincular a referência antes da resposta de criação. Um webhook transacional
sem vínculo disponível recebe 503, permitindo reentrega. Com ID conhecido, novo POST do aluno
consulta o Checkout para recuperar notificações perdidas; GET nunca chama o gateway nem grava.
Sem ID e sem notificação, é necessária conciliação operacional com o PagBank; **não apagar a
reserva, não inventar expiração e não repetir POST por timeout**. Depois de o Checkout ser
comprovadamente expirado e não haver pagamento pendente, uma nova tentativa pode ser criada.

A consulta usa até 100 pedidos; uma página cheia é tratada como incompleta e bloqueia a
conciliação automática. Não deduzir ausência de pagamento a partir de uma página incompleta.
Respostas com formatos ou estados novos requerem revisão do adapter, nunca liberação automática.

A aprovação é persistida antes da atualização condicional da matrícula
(`aceita_aguardando_pagamento` → `ativa`, `acesso_liberado=true`). O evento só é marcado depois.
Falha entre escritas é recuperada na reentrega. Não são necessárias transações Mongo.
Canceladas/recusadas não são reativadas: aprovação tardia mantém `conciliacao_necessaria=true`
para avaliação/estorno fora deste escopo. Estados pagos nunca regridem.

## Validação

Testes de Mongo usam Testcontainers com Mongo 7 isolado e o DDL real; requerem Docker.
Executar:

```sh
./mvnw -Dtest=PagBankMatriculaClientTest,PagamentoMatriculaServiceTest,PagamentoMongoTest,PagamentoFluxoMvcTest,DoacaoServiceTest,DoacaoFluxoMvcTest test
./mvnw test
```

O teste de contexto existente exige as variáveis de configuração descritas em `AGENTS.md`.
Para testes locais, usar Mongo isolado e valores fictícios de mail/OAuth/Cloudinary; não é
necessário conectar esses serviços nem fornecer credenciais PagBank aos testes automatizados.

### Roteiro ponta a ponta Sandbox

1. Professor aceita a solicitação: estado aguardando pagamento, vaga reservada, acesso bloqueado.
2. Dono/aluno abre resumo e envia formulário com CSRF: uma reserva, Checkout persistido e HTTP 303 para PAY Sandbox.
3. Voltar sem pagar, inclusive acrescentando `status=PAID`: continua aguardando, sem acesso.
4. Usar exclusivamente instrumentos fictícios oficiais do PagBank na página Sandbox.
5. Após webhook autenticado, verificar pagamento aprovado, matrícula ativa e acesso liberado.
6. Reentrega do mesmo evento preserva resultado. Repetir com recusa, expiração e matrícula cancelada.
7. Não imprimir tokens, assinaturas, payloads, dados pessoais ou de pagamento; inspecionar somente IDs e estados.

Na execução de desenvolvimento, a criação real no Sandbox retornou HTTP 403 com a credencial
local disponível. Assim, aprovação hospedada, webhook real via ngrok e duplicata real permanecem
pendentes de credencial/permissão válida. Os cenários automatizados usam doubles de PagBank;
não devem ser apresentados como homologação ponta a ponta.

Validação automatizada final: `./mvnw test` passou com 52 testes, sem falhas, erros ou testes
ignorados, usando Mongo isolado e configuração fictícia dos serviços externos. Inclui regressões
de doações, MVC/CSRF, corpo bruto/assinatura, concorrência, cancelamento e recuperação de falhas
parciais. A migração também foi executada em banco isolado: execução repetida é idempotente e
registros legados bloqueiam a migração sem serem apagados.
