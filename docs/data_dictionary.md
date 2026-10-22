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
| status\_atual | string enum \[pendente, aprovado, recusado, vencido, estornado\] | sim | RN050 |
| metodo\_pagamento | string enum \[pix, cartao\] ou null | não | — |
| mes\_referencia | date (mês/ano da cobrança) | sim | RN049, RN052 |
| data\_vencimento | date | sim | RN056 |
| data\_pagamento | datetime ou null | não | — |
| tentativas | array embutido \[{transacao\_externa\_id, status, criado\_em}\] | sim, a partir da 1ª tentativa | RN051 (histórico de tentativas); RN054 (nunca guardar dado sensível de cartão, só a referência do gateway) |
| criado\_em | datetime | sim | — |

**Coleção: webhooks\_processados**

OBS: existe só para deduplicar notificações assíncronas da API de pagamento; gateways garantem entrega "pelo menos uma vez" e podem reenviar o mesmo evento.

| Campo | Tipo | Obrigatório | Regra de Negócio / Requisitos |
| :---- | :---- | :---- | :---- |
| \_id | ObjectId | sim | — |
| evento\_externo\_id | string (índice único) | sim | evita aplicar o mesmo webhook duas vezes |
| processado\_em | datetime (índice TTL, expira em \~30 dias) | sim | — |

### **2.5.2 Justificativas de Referência** {#2.5.2-justificativas-de-referência}

	Foi decidido o que seria armazenado apenas como referência e o que seria acoplado junto a uma coleção, com base nas seguintes justificativas:

| Relação | Decisão | Justificativa |
| :---- | :---- | :---- |
| curso \-\> horários | embed | pequeno, limitado, sempre lido junto |
| curso \-\> professor | reference (+ nome denormalizado) | professor existe independente do curso, mas é comum exibir o nome do professor junto ao curso que ele ministra |
| matrícula \-\> aluno / curso | reference | ambos existem/são consultados independentemente |
| matrícula \-\> pagamentos | reference | cresce sem limite ao longo do tempo; status consultado direto na coleção |
| pagamento \-\> tentativas | embed | pequeno, limitado, sempre lido junto, sem relevância fora do pagamento |
