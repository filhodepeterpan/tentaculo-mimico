### **2.5.1 Dicionário de Dados** {#2.5.1-dicionário-de-dados}

**Coleção: usuarios**

OBS: Unifica ALUNO, PROFESSOR e ADMINISTRADOR num único documento com discriminador

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos  |
| :---- | :---- | :---- | :---- |
| \_id | ObjectId | sim | — |
| tipo\_usuario | string enum \[aluno, professor, administrador\] | sim | RN004, RN018 (imutável após criação) |
| nome | string | sim | — |
| email | string (único) | sim | RN006, RN007 |
| cpf | string (único) | sim | RN006, RN008 |
| data\_nascimento | date | sim | — |
| autenticacao.senha\_hash | string ou null | condicional | RN005 — null quando o login é só via Google |
| autenticacao.provedor | string enum \[local, google\] | sim | suporte a login Google |
| autenticacao.provedor\_id | string ou null (índice único composto com provedor) | condicional | sub do Google, evita duplicar conta |
| email\_confirmado | boolean | sim | RN010 (conta Google já vem confirmada automaticamente) |
| tentativas\_login\_falhas | number | sim | RN009 |
| bloqueado\_ate | datetime ou null | não | RN009 |
| ativo | boolean | sim | RN020 — exclusão lógica, nunca apagar o documento |
| criado\_em / atualizado\_em | datetime | sim | — |

**Coleção: cursos**

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos |
| :---- | :---- | :---- | :---- |
| \_id | ObjectId | sim | — |
| professor\_id | ObjectId (ref usuarios) | sim | RN022 |
| professor\_nome | string (denormalizado) | sim | \- |
| nome | string | sim | RN022 |
| descricao | string | sim | RN022 |
| carga\_horaria | number (horas totais) | sim | RN022 |
| horarios | array embutido \[{dia\_semana, hora\_inicio, hora\_fim}\] | sim | RN026, RN033 — checagem de conflito de horário |
| preco | decimal | sim | RN022 |
| vagas\_totais | number | sim | RN022, RN024, RN029 |
| vagas\_disponiveis | number | sim | RN029, RN031, RN030 — operação atômica (condicional) |
| status | string enum \[rascunho, disponivel, indisponivel\] | sim | RN023, RN027, RN028, RN030 |
| data\_inicio / data\_fim | date | data\_inicio sim, data\_fim opcional | RN037 |
| data\_cadastro | date | sim | — |
| ativo | boolean | sim | RN025 |

**Coleção: matriculas**

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos |
| :---- | :---- | :---- | :---- |
| \_id | ObjectId | sim | — |
| aluno\_id | ObjectId (ref usuarios) | sim | — |
| curso\_id | ObjectId (ref cursos) | sim | — |
| status | string enum \[pendente, aceito\_aguardando\_pagamento, ativa, recusada, cancelada\] | sim | RN040, RN042, RN043, RN044 |
| acesso\_liberado | boolean | sim | RN053, RN055, RN058 |
| data\_solicitacao | datetime | sim | RN038 |
| data\_resposta\_professor | datetime ou null | não | RN041, RN042 |
| data\_cancelamento | datetime ou null | não | RN044, RN035 |

**Coleção: pagamentos**

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos |
| :---- | :---- | :---- | :---- |
| \_id | ObjectId | sim | — |
| matricula\_id | ObjectId (ref matriculas) | sim | — |
| valor | decimal | sim | — |
| status\_atual | string enum \[PENDENTE, APROVADO\] | sim | Cobrança inicial; aprovação nunca regride por evento atrasado |
| versao | long | sim | Optimistic locking: reserva e atualizações condicionais |
| conciliacao\_necessaria | boolean | sim | Pagamento tardio de matrícula cancelada/recusada ou múltiplas capturas; requer avaliação/estorno externo |
| mes\_referencia | date (primeiro dia UTC do mês da cobrança inicial) | sim | Não gera mensalidades |
| data\_vencimento | date | sim | Prazo inicial solicitado; expiração efetiva em cada tentativa |
| tentativas | array embutido | sim | Histórico preservado, inclusive falhas e resultados incertos |
| criado\_em | datetime | sim | — |

Cada tentativa contém `reference_id` (UUID opaco), `checkout_id` opcional, `pay_url` opcional
(exclusivamente Sandbox), `expira_em`, `criado_em`, `falhou_em`, `status`
(`CRIANDO`, `INCERTA`, `WAITING`, `IN_ANALYSIS`, `PAID`, `DECLINED`, `CANCELED`, `EXPIRED`),
`transacao_externa_id`, `data_pagamento` e `metodo` quando aprovado. `transacoes` guarda somente
ID externo, estado, `valor_centavos` inteiro, moeda, método e instante de pagamento de cada cobrança associada.
Nunca armazenar PAN, CVV, token, dados pessoais do comprador ou corpo integral de notificações.

Índices únicos: `matricula_id` (uma cobrança **inicial** por matrícula), `tentativas.reference_id`,
`tentativas.checkout_id`, `tentativas.transacao_externa_id` e `tentativas.transacoes.transacao_externa_id`
(os três últimos esparsos). Índices são garantidos no startup; falha de criação impede iniciar a aplicação.
Uma nova tentativa só é reservada após `EXPIRED` confirmado em consulta autenticada, sem transações pendentes.
Recusa permite retomar o mesmo Checkout válido, nunca criar outro em paralelo. Timeout fica `INCERTA`;
não se presume falha de criação e não há retry automático do POST.

**Coleção: webhooks\_processados**

OBS: existe só para deduplicar notificações assíncronas da API de pagamento; gateways garantem entrega "pelo menos uma vez" e podem reenviar o mesmo evento.

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos |
| :---- | :---- | :---- | :---- |
| \_id | string (SHA-256 hexadecimal) | sim | Chave estável sem dados pessoais |
| evento\_externo\_id | string (índice único) | sim | Mesma chave: ID de evento, referência, checkout e estados/instantes das transações conciliadas |
| processado\_em | datetime (índice TTL, expira em \~30 dias) | sim | — |

### **2.5.2 Justificativas de Referência** {#2.5.2-justificativas-de-referência}

	Foi decidido o que seria armazenado apenas como referência e o que seria acoplado junto a uma coleção, com base nas seguintes justificativas:

| Relação | Decisão | Justificativa |
| :---- | :---- | :---- |
| curso \-\> horários | embed | pequeno, limitado, sempre lido junto |
| curso \-\> professor | reference (+ nome denormalizado) | professor existe independente do curso, mas é comum exibir o nome do professor junto ao curso que ele ministra |
| matrícula \-\> aluno / curso | reference | ambos existem/são consultados independentemente |
| matrícula \-\> pagamentos | reference | uma cobrança inicial por matrícula neste escopo; mensalidades não implementadas |
| pagamento \-\> tentativas | embed | pequeno, limitado, sempre lido junto, sem relevância fora do pagamento |
