# Plano — Doações com Checkout hospedado do PagBank

**Situação:** planejamento; nenhuma funcionalidade, configuração ou migração implementada.
**Base da análise:** código atual e documentação oficial do PagBank consultada em 18/10/2026; contratos de criação e assinatura reconferidos nesta revisão.

## 1. Correção de escopo e objetivo da sprint

Este documento substitui `docs/plano-checkout-pagbank.md`. A tarefa desta sprint é **colocar a página de doações em funcionamento com pagamentos avulsos pelo Checkout hospedado do PagBank**, deixando uma base técnica reaproveitável para pagamentos de cursos em outra sprint.

O plano anterior tratou incorretamente pagamentos de cursos como objetivo. Nele, o usuário havia registrado “limitar a entrega à cobrança inicial”. Essa decisão fica preservada aqui como histórico, mas **foi superada pela correção explícita de escopo para doações**; não autoriza cobrança inicial de curso nesta entrega.

### Entrega esperada

1. Visitante ou usuário logado escolhe valor e mensagem opcional na página de doações.
2. O backend valida e persiste uma intenção de doação, ainda não paga.
3. Uma tela local mostra valor, mensagem e destinatário para confirmação.
4. Ao confirmar, o backend cria ou reutiliza um checkout; o navegador segue para a URL PAY do PagBank.
5. O doador escolhe e utiliza os meios de pagamento na página hospedada, sem formulários próprios de cartão/Pix.
6. Webhook autenticado e consulta à API atualizam o pagamento persistido. Somente captura `PAID` validada confirma recebimento da doação.
7. A tela de retorno mostra o estado real: aguardando, em análise, recebida, recusada, cancelada, expirada ou em atendimento. O processamento independe de o doador voltar ao site.

**Não entram nesta sprint:** mensalidade ou compra inicial de curso, recorrência de cursos/doações, matrícula, vagas, aprovação do professor, liberação de aulas, suspensão por inadimplência, split, painel financeiro completo, recibo fiscal/dedutibilidade, mural público de mensagens ou API de reembolso automático. Não criar tela de cobrança recorrente nem chamar `MatriculaService.confirmarPagamento` a partir desta integração.

**Reúso pretendido:** configuração, cliente HTTP, contratos PagBank, assinatura, persistência financeira, criação controlada de checkout e reconciliação. Isso reduz trabalho futuro sem antecipar regras de cursos ou um framework genérico de pagamentos.

## 2. Diagnóstico do código atual

Caminhos Java abaixo são relativos a `src/main/java/br/com/tentaculomimico/`.

| Arquivo/componente real | Situação observada | Ação proposta |
| --- | --- | --- |
| `controller/PaginasController.java`, método `doacoes` | `GET /doacoes` renderiza `doacoes` sem exigir login | Preservar página pública; adicionar preparo de chave de submissão e dados necessários via controller/service, sem duplicar GET |
| `controller/DoacaoController.java` | Implementa POST `/doacoes`, GET `/pagamentos/doacao` e POST `/pagamentos/doacao/confirmar`; concentra validação e simulação | Manter controller de navegação; mover regra para `DoacaoService`; substituir confirmação simulada por checkout |
| `DoacaoController.DoacaoPendente` | Record com valor, mensagem e código Pix, armazenado em `HttpSession` como `doacaoPendente` | Substituir sessão como fonte de verdade por documento persistido; sessão passa a guardar apenas referências de acesso/intenção |
| `DoacaoController.iniciar` | Valida valor entre R$1 e R$10.000, trunca mensagem a 500 caracteres e sobrescreve uma única doação na sessão | Preservar limites como regra inicial proposta; tratar abas/doações distintas sem sobrescrever uma intenção com checkout aberto |
| `DoacaoController.lerValor` | Aceita vírgula/ponto e aplica `HALF_UP` a duas casas; backend não restringe integralmente a gramática como o JS | Validar formato/precisão no backend e converter para centavos exatos; não arredondar valor financeiro silenciosamente |
| `DoacaoController.confirmar` | Aceita `metodo` (`pix`, `credito`, `debito`); com `app.pagamento.simulado=true` remove a sessão e mostra sucesso por flash | Remover esse caminho de aprovação para doações; agradecimento de recebimento depende de pagamento persistido verificado |
| `controller/PagamentoController.java` | Fluxo de cursos separado no backend, mas usa o mesmo template; também utiliza a flag simulada | Não integrar cursos nesta sprint; evitar mudanças acidentais ao separar a apresentação |
| `service/SessaoService.java` | Identidade local via `usuarioLogadoId` da sessão; não exige login para doar hoje | Usar apenas para vincular opcionalmente o usuário; nunca exigir tipo ALUNO nem depender de sessão em webhook/job |
| `security/SecurityConfig.java` | `/doacoes` e `/pagamentos/**` são `permitAll`; CSRF desabilitado globalmente; outras rotas podem cair em `.authenticated()` | Liberar explicitamente novos caminhos necessários; proteger rotas de doação contra CSRF e leitura cruzada; webhook usa assinatura |
| `model/` e `repository/` | Existem usuários, cursos e matrículas; não há persistência de pagamentos/doações | Criar persistência financeira independente, sem referências obrigatórias de curso/matrícula |

### 2.1 Frontend que pode ser aproveitado

- `src/main/resources/templates/doacoes.html`: contexto institucional, valores R$2/5/10/15/35/75, campo “Outro”, mensagem opcional e formulário POST `/doacoes`. Valor e mensagem são campos ocultos preenchidos por JS.
- `src/main/resources/static/js/pages/doacoes.js`: alterna opções/outro, valida mínimo e até duas casas, copia valores para campos ocultos. Não verifica o teto de R$10.000; JavaScript pode ser desabilitado/adulterado e não substitui validação do servidor. Ajustar submissão para que a página não dependa exclusivamente de JS para obter valor/mensagem válidos.
- `src/main/resources/static/css/pages/doacoes.css`: estilos da página inicial reaproveitáveis.
- `src/main/resources/templates/pagamento.html`: resumo, formulário local de cartão, Pix simulado e agradecimento com `ehDoacao=true`; também exibe confirmação de matrícula quando `ehDoacao=false`.
- `static/js/pages/pagamento.js`: QR local, contador, validação de cartão e envio de `metodo`. Nada disso será usado no fluxo de doações hospedado.
- `static/css/pages/pagamento.css` e `static/img/doacoes-icon.png`: reaproveitar apresentação de resumo/agradecimento, sem carregar JS de cartão/Pix na nova tela de doação.

**Separação necessária:** criar telas dedicadas de doação, por exemplo `doacao-confirmacao.html` e `doacao-resultado.html`. É a transformação da tela de pagamento **do fluxo de doação**, não da tela de curso. Não apagar globalmente `pagamento.html`, JS, CSS, vendor QR ou componentes de cartão que ainda têm consumidores em cursos/galeria. O fluxo legado de cursos continua explicitamente simulado e não deve ser apresentado como pagamento real pronto para produção.

### 2.2 Documentação versus código

- `docs/requisitos.md` descreve pagamentos relacionados a matrículas e mensalidades. Essas regras continuam sendo intenção de **outra sprint**, não requisitos implícitos da doação. Não reutilizar RN048 (aprovação do professor) ou RN053 (acesso ao curso) como justificativa de regras de doação.
- `docs/data_dictionary.md` descreve `pagamentos` com `matricula_id` e competência mensal obrigatórios. Esse modelo **não atende doação avulsa pública** e ainda não existe no código. A implementação deve documentar a extensão/revisão proposta, não inserir matrícula fictícia para satisfazer o dicionário.
- O dicionário prevê `webhooks_processados.evento_externo_id`, mas os exemplos de Checkout têm IDs de recursos, não um identificador único de evento confirmado. Não inventar essa garantia.
- A flag `app.pagamento.simulado` não está declarada no properties principal; os controllers usam default `true`. Ela é compartilhada com cursos: retirar sua influência sobre doações reais sem alterar implicitamente a política dos cursos.
- O projeto usa **Boot 4.1.1 / Java 21**, `spring-boot-starter-webmvc` e starters de teste por módulo. Só existe `TentaculoMimicoApplicationTests.contextLoads`, sem cobertura financeira ou perfil de teste dedicado.
- A observação do `AGENTS.md` sobre trocar `spring.mongodb.uri` deve ser revista antes de qualquer alteração: na análise anterior, os metadados locais do Boot 4.1.1 indicaram `spring.mongodb.uri` como atual e `spring.data.mongodb.uri` como obsoleta. Não aplicar receita de Boot 3 automaticamente.

Não houve acesso ao banco nem criação de pagamento no Sandbox. Validadores, índices, topologia Mongo e respostas reais do provedor permanecem itens de verificação na implementação. Nenhum arquivo de requisitos/dicionário foi reescrito nesta revisão.

## 3. Decisões de domínio e arquitetura

### 3.1 Doação avulsa, pública e independente de usuário

- Manter doação sem login, inclusive para visitantes. Usuário logado pode ter `usuario_id` associado no servidor, mas não é obrigado a ser aluno. CPF/e-mail retornados pelo PagBank não autenticam uma conta local.
- Uma intenção corresponde a um valor fixo, uma mensagem e uma contribuição voluntária para a conta única do Tentáculo Mímico. Não enviar mensagem pessoal ao PagBank como descrição do item.
- Proposta inicial: manter R$1 a R$10.000 e mensagem opcional até 500 caracteres. Validar também os limites realmente aceitos pela conta/método no Sandbox; não prometer que todo meio aceitará qualquer valor desse intervalo.
- Rejeitar precisão maior que dois centavos, sinal negativo, notação científica e formatos ambíguos não previstos; normalizar os formatos pt-BR já suportados conscientemente. Mensagem é texto, não HTML; tratar ausência com segurança e escapar por `th:text`.
- Confirmada a intenção, valor e mensagem tornam-se snapshot. “Alterar doação” cria outra intenção após aviso; não modifica checkout emitido. Havendo pagamento em andamento, alertar para não pagar duas vezes.
- Múltiplas doações **intencionais** da mesma pessoa são permitidas. Deduplicar requisições da mesma intenção, não proibir duas doações por usuário/e-mail/CPF/valor.
- Só considerar recebida a doação após verificação de `PAID`. `ACTIVE`, `AUTHORIZED`, retorno do navegador e botão de confirmação local não provam recebimento.

### 3.2 Persistência simples, preparada para extensão

Proposta: **uma nova coleção `pagamentos`**, com `finalidade=doacao` e dados da doação embutidos no documento financeiro. Evitar duas coleções `doacoes`/`pagamentos` com relação 1:1 e status duplicados nesta entrega: o documento representa a intenção e seu pagamento, atualizáveis atomicamente. A UI usa um view model de doação, sem expor DTO do PagBank.

| Campo proposto | Tipo / regra |
| --- | --- |
| `_id` | ObjectId, ID interno da intenção/pagamento |
| `finalidade` | Texto `doacao`; não implementar finalidade de curso ainda |
| `usuario_id` | ObjectId opcional, preenchido apenas da sessão local autenticada |
| `doacao.mensagem` | Texto opcional limitado; privado nesta entrega |
| `valor_centavos`, `moeda` | Inteiro longo e `BRL`, snapshot imutável |
| `chave_submissao` | Identificador aleatório emitido pelo servidor, único, ligado à intenção de formulário |
| `status_atual` | `pendente`, `aprovado`, `recusado`, `cancelado`, `vencido`, `estornado`, `em_revisao` |
| `tentativas[]` | UUID/referência externa da tentativa, estado de criação, checkout ID, URL PAY, status/expiração do checkout, pedidos/charges e seus estados/valores/datas |
| `charge_aprovada_id`, `pago_em` | Evidência da confirmação; sem contador que incremente por webhook |
| `criado_em`, `atualizado_em`, `ultima_consulta_em`, `proxima_consulta_em` | UTC (`Instant`), auditoria e recuperação |
| `revisao`, `lock_ate`, `lock_dono` | CAS e exclusão temporária persistida em criação/reconciliação |
| `motivo_revisao`, `ultimo_erro_codigo` | Códigos saneados de falha/divergência/duplicidade |

Separar estado da criação (`criando`, `criado`, `falha_definitiva`, `resultado_desconhecido`), estado do checkout e estado financeiro. Não adicionar `matricula_id`, competência, professor ou acesso como obrigatórios. Ao integrar cursos futuramente, acrescentar vínculo próprio por finalidade e índices condicionais adequados; não herdar unicidade por usuário.

Índices: chave de submissão única; referência de tentativa e checkout ID únicos parciais quando presentes; status/próxima consulta; consultas por usuário opcional se necessárias. Usar CAS ao incluir tentativas: índice único multikey não elimina duplicatas dentro do mesmo documento. Validar índices e `$jsonSchema` no Mongo real antes da migração; mapear explicitamente snake_case e ObjectId nas novas classes.

Persistir apenas IDs e evidências saneadas das charges: estado, método, valor/moeda, montantes pagos/estornados e datas. Não guardar token, corpo bruto de webhook, CPF/endereço do pagador, cartão, validade, CVV ou código Pix. Mensagem pode conter dados pessoais: não colocar em logs, URLs ou painel público. Definir limite de tentativas/rate limit para não crescer indefinidamente o array.

**Sem coleção de deduplicação obrigatória:** a primeira versão pode processar webhook sincronamente e reconciliar pelo documento financeiro. Idempotência deve estar na atualização dos fatos, não em “já vi este hash, então ignoro tudo”. Se uma caixa de entrada de webhooks for necessária depois, não pressupor ID de evento externo que o contrato não fornece.

### 3.3 Classes e fronteiras de reúso

| Classe proposta | Responsabilidade |
| --- | --- |
| `config/PagBankProperties` e bean `RestClient` | Credencial, ambiente/base permitida, URLs públicas, expiração e timeouts; `@ConfigurationProperties` validada |
| `service/PagBankClient` + `dto/pagbank/` | Criar/consultar/inativar checkout, consultar pedidos/charges; DTOs independentes de doação ou matrícula |
| `model/Pagamento`, tipos embutidos e `repository/PagamentoRepository` | Estado financeiro, referências e operações condicionais; sem sessão HTTP |
| `service/PagamentoService` | Criação/reúso, reconciliação, montantes, estados, tentativas e recuperação; sem chamar matrícula ou decidir acesso a cursos |
| `service/DoacaoService` | Valor/mensagem, intenção, autorização para visualizar/confirmar doação, vínculo opcional de usuário; delega operações financeiras |
| `controller/DoacaoController` | Navegação Thymeleaf, formulários e mensagens pt-BR; sem acesso direto à API |
| `controller/PagBankWebhookController` + validação de assinatura | Corpo bruto, autenticação e resposta HTTP; delega reconciliação comum; sem sessão |
| `service/PagamentoReconciliacaoJob` | `@Scheduled`, lotes pequenos, lock por documento e retentativas limitadas |

Não criar interface de gateway com um único implementador, event bus, hierarquia de estratégias por produto ou handlers vazios de curso. O contrato local de criação recebe valor/item/referência/URLs já validados, não um objeto `DoacaoController.DoacaoPendente` nem uma `Matricula`. Operações atômicas especiais ficam em implementação de persistência com `MongoTemplate`, não em controllers.

**Extensão futura de cursos:** outro serviço de aplicação poderá criar pagamentos para matrícula/competência usando o mesmo cliente, assinatura e reconciliação. Essa sprint futura definirá pré-condições, cobrança recorrente, reservas e consequência de `aprovado` no domínio. A persistência da aprovação continua independente; eventual efeito na matrícula deverá ser recuperável. Nenhuma dessas transições é implementada agora.

### 3.4 Identidade do visitante, abas e autorização

Doação pública não significa consulta financeira pública por ObjectId.

- No GET do formulário, emitir chave aleatória de submissão e registrá-la na sessão como intenção autorizada. Diferentes abas/formulários podem ter chaves diferentes; retries do mesmo formulário usam a mesma chave.
- No POST, validar a chave contra a sessão e fazer criação única no Mongo. Se já existir, recuperar a mesma intenção, sem alterar snapshot. Payload diferente com a mesma chave exige nova confirmação/nova chave, nunca alteração silenciosa.
- Guardar na sessão o conjunto limitado de IDs de doação acessíveis; não usar só um `doacaoPendente` que a segunda aba sobrescreve. Não persistir ID bruto da sessão como dado financeiro nem confiar em `doacaoId` recebido para conceder acesso.
- Rotas de confirmação/resultado/atualização exigem posse na sessão original; opcionalmente permitir também ao usuário logado cujo `usuario_id` coincide. Não vincular uma doação de visitante à primeira conta que abrir a URL depois.
- Retorno do PagBank usa `doacaoId`, sem token de acesso na query string. Perdeu a sessão e não existe usuário vinculado? Exibir orientação genérica, sem valor/mensagem/dados do pagador. A doação continua sendo reconciliada normalmente. Recuperação por e-mail/link secreto fica fora do MVP, salvo decisão expressa.
- Um novo GET do formulário não pode invalidar a chave de outra aba ainda válida; limitar quantidade/tempo das intenções em sessão. A chave de idempotência não substitui CSRF e não deve ser usada sozinha como autorização pública de leitura.

## 4. Contrato de integração PagBank

Referências oficiais numeradas na seção 9. Não usar APIs legadas/XML ou Checkout transparente.

### 4.1 Endpoints e credenciais

Base Sandbox: `https://sandbox.api.pagseguro.com`; produção: `https://api.pagseguro.com` [7]. Usar Bearer no backend; JSON na criação.

| Operação | Endpoint | Utilização |
| --- | --- | --- |
| Criar | `POST /checkouts` | Retorna `CHEC_...`, status, expiração e links [2] |
| Consultar checkout | `GET /checkouts/{id}?offset=0&limit=100` | Checkout e pagamentos associados; paginar [3] |
| Consultar pedido | `GET /orders/{id}` | `ORDE_...` e charges [6] |
| Consultar pagamento | `GET /charges/{id}` | `CHAR_...`, estado financeiro e montantes [6] |
| Inativar | `POST /checkouts/{id}/inactivate` | Impede novas operações pelo link; não estorna nem invalida automaticamente instrumento já emitido [1] |

Para a conta única própria, os endpoints usados documentam `Authorization: Bearer <token>`. Não acrescentar Connect de terceiros, SDK JS, chave pública para cartão ou mTLS de Transferência/Pix Bacen sem necessidade demonstrada [2, 8, 9, 12].

### 4.2 Payload proposto para doação

Exemplo estrutural; valores e URLs devem vir de configuração/documento do servidor:

```json
{
  "reference_id": "<UUID-da-tentativa-persistida>",
  "expiration_date": "<ISO-8601-com-fuso>",
  "customer_modifiable": true,
  "items": [{
    "reference_id": "<id-interno-da-doacao>",
    "name": "Doação ao Tentáculo Mímico",
    "quantity": 1,
    "unit_amount": 3500
  }],
  "redirect_url": "https://<dominio>/pagamentos/doacao/<id>/retorno",
  "return_url": "https://<dominio>/pagamentos/doacao/<id>/retorno",
  "notification_urls": ["https://<dominio>/api/pagbank/webhooks/checkout"],
  "payment_notification_urls": ["https://<dominio>/api/pagbank/webhooks/pagamento"]
}
```

- Uma contribuição de R$35 vira `unit_amount=3500`, calculado de `BigDecimal` com conversão exata, sem `double`. A partir da confirmação, ignorar valores alternativos enviados pelo navegador: usar snapshot persistido.
- Referência externa até 64 caracteres; uma UUID por tentativa, gravada **antes** da chamada. Não usar nome/CPF do doador como chave. Item e descrição são fixos no servidor, sem mensagem pessoal.
- Omitir frete, descontos, adicionais e `recurrence_plan`. Omitir `customer` com `customer_modifiable=true` inicialmente, deixando o PagBank coletar dados; validar essa configuração na prova Sandbox. `false` exige dados completos, inclusive telefone ausente do model local [2]. Doar sem login local **não significa pagamento sem identificação no PagBank**.
- Expiração explícita proposta: duas horas, configurável. O guia e a referência divergem sobre o default; não depender de comportamento implícito [1, 2].
- Persistir ID, estado/expiração e `links[].href` onde `rel=PAY`; não presumir posição no array nem construir o link manualmente. Validar HTTPS/host oficial por ambiente. Após POST, redirecionar com 303 somente após gravação da resposta.
- Configurar tanto URLs de checkout quanto de pagamento. URLs de notificação têm limite documentado de 100 caracteres; URLs de retorno, 255. Gerar de uma base pública confiável, nunca de `Host` arbitrário.
- Referência de criação documenta HTTP 200. Resposta sem ID/link PAY válido não é checkout utilizável. `return_url`/`redirect_url` só levam ao resultado local: query string não aprova, recusa ou cancela pagamento.

### 4.3 Métodos de pagamento

O guia atual lista crédito, débito, Pix, boleto, Pagar com PagBank, Apple Pay e Google Pay [1]. Os enums explícitos de `payment_methods[].type` são `CREDIT_CARD`, `DEBIT_CARD`, `PIX`, `BOLETO`; débito exige aprovação interna. Pagar com PagBank permanece disponível segundo o guia. Não inventar enums de carteira ou prometer todos os métodos em todo ambiente/dispositivo.

Preferência: usar métodos habilitados no checkout da conta, sem seletor local. Confirmar se omitir `payment_methods` produz esse comportamento; caso contrário, configurar explicitamente os meios contratados. Propor cartão em uma parcela (`INSTALLMENTS_LIMIT=1`) para evitar ambiguidade de juros nesta entrega. Parcelamento, se desejado, exige decisão sobre taxas e validação dos montantes; não aceitar divergência financeira arbitrária. Nenhum método exige formulário próprio de cartão/Pix no site.

### 4.4 Webhooks e verificação real

1. Receber corpo bruto e `x-authenticity-token`; limitar tamanho e não desserializar/reformatar antes de verificar assinatura.
2. Calcular **SHA-256 de `token + "-" + corpoOriginal` em UTF-8** e comparar com assinatura hexadecimal em tempo constante [5]. Usar JDK (`MessageDigest`/`HexFormat`); não é HMAC e não precisa dependência adicional. Rejeitar assinatura ausente/inválida mesmo em URL pública.
3. Checkout notifica objeto `CHEC_...`; pagamento é exemplificado como pedido `ORDE_...` com `charges[]` `CHAR_...` [4]. IDs raiz são de recurso, não de evento. Confirmar propagação de `reference_id` para pedidos gerados pelo Checkout no Sandbox.
4. Resolver a tentativa persistida e consultar API com o token do backend, no host configurado. Não seguir URL arbitrária do webhook (SSRF). Verificar associação checkout → pedido/charge, referência, valor, BRL e montante capturado/estornado. Um `PAID` de outra transação não confirma a doação.
5. Consultar checkout com paginação. A documentação promete pagamentos associados, mas o exemplo de resposta omite o array correspondente [3]. Capturar fixture real saneada antes de fixar DTO/caminho de extração; usar pedido/charge como complemento, não como substituto da associação comprovada.
6. Aplicar atualização condicional idempotente ao documento e responder 200/204 depois de persistir. Assinatura inválida: 401/403; JSON inválido/tamanho excessivo: 400/413; indisponibilidade temporária: 503 e recuperação pelo job. Não depender exclusivamente de reentrega do provedor.

Se evento chegar antes de gravar resposta de criação, referência da tentativa já persistida permite reconciliação/associação segura. Evento desconhecido não cria doação automaticamente: registrar ocorrência saneada e permitir atendimento/reprocessamento, sem esconder perda de associação. A documentação consultada não confirma ID único de evento, ordenação, SLA ou cronograma completo de retries. Não inventar garantias.

### 4.5 Estados, falhas e idempotência

| Evidência atual consultada | Estado/efeito da doação |
| --- | --- |
| Intenção/checkout criado, sem captura | Pendente; nenhuma afirmação de recebimento |
| `WAITING`, `IN_ANALYSIS`, `AUTHORIZED` | Aguardando/em análise; autorização de cartão não é captura |
| `PAID` associado e montantes válidos | Aprovado; exibir agradecimento por recebimento confirmado |
| `DECLINED` | Recusa da tentativa; pode tentar de novo no checkout utilizável, sem anular outra charge paga |
| `CANCELED` sem captura | Cancelamento da charge; consolidar somente após considerar demais tentativas |
| Checkout `EXPIRED`/`INACTIVE` | Estado do link, não prova de cancelamento de boleto/Pix já emitido |
| Sem captura nem instrumento ainda pagável e validade encerrada | Vencido; liberar nova tentativa controlada |
| Estorno confirmado | Estornado ou revisão para parcial/ambíguo; preservar evidência e ajustar resultado, sem inventar enum externo |
| Charge paga duplicada, valor/moeda divergente ou associação inconclusiva | Em revisão/atendimento; registrar valor recebido, não ocultá-lo nem contabilizar duas vezes |

Regras operacionais:

- Chave única da submissão evita duas intenções no duplo POST inicial; lock/CAS por pagamento evita dois checkouts simultâneos. Reutilizar checkout conhecido e ainda utilizável da mesma intenção. Não bloquear doação nova explicitamente escolhida pelo doador.
- Persistir tentativa antes da rede e resposta antes de redirect. Se processo cair, retomar pelo estado persistido. Sessão perdida, logout ou retorno ausente não apagam doação nem impedem webhook/job.
- **Idempotência externa não confirmada:** documento geral menciona chaves [12], mas `POST /checkouts` não documenta header/validade/garantia específica. `reference_id` não prova unicidade externa. Confirmar contrato com Sandbox/suporte; não inventar header nem retry seguro.
- Sem garantia confirmada, timeout/queda após envio vira `resultado_desconhecido`: bloquear criação automática de outro checkout, reconciliar por referência/webhook ou resolver no portal. Não há busca de checkout por referência confirmada neste plano. Lock expirado não autoriza repetir POST incerto.
- Recusa de uma charge não encerra todas as tentativas; expiração/inativação do checkout não invalida instrumentos emitidos. Antes de nova tentativa, consultar todas as conhecidas e impedir sobreposição pagável. Em dúvida, aguardar ou encaminhar para atendimento.
- Não aplicar ranking linear de estados nem confiar na ordem dos POSTs. Serializar reconciliação com lock persistido/revisão CAS para impedir resposta antiga de sobrescrever a nova. Guardar estado por charge; recusa antiga não desfaz doação recebida, mas estorno legítimo deve ser considerado.
- Job em lote proposto a cada minuto para pendências/erros, atraso progressivo e limite de chamadas. Revisitar aprovados em frequência menor para alterações financeiras não notificadas. `404`, 429, 5xx e timeout não são confirmação de recusa/expiração. São parâmetros locais, não SLA do PagBank.
- Não criar total arrecadado/contador público nesta sprint. Se necessário depois, derivar de fatos financeiros únicos e estornos, nunca incrementar a cada webhook.

## 5. Rotas, segurança e interface

### 5.1 Rotas propostas

| Rota | Uso/proteção |
| --- | --- |
| `GET /doacoes` | Página pública; prepara chave de submissão sem criar checkout |
| `POST /doacoes` | Valor/mensagem/chave; CSRF + validação + intenção única; redirect para confirmação persistida |
| `GET /pagamentos/doacao/{doacaoId}` | Resumo/confirmação; posse da intenção em sessão ou usuário vinculado; não cria checkout |
| `POST /pagamentos/doacao/{doacaoId}/checkout` | Mesma autorização + CSRF/rate limit; cria ou reutiliza e redireciona para PAY |
| `GET /pagamentos/doacao/{doacaoId}/retorno` | Resultado persistido; mesma autorização; não confia em parâmetros de aprovação |
| `POST /pagamentos/doacao/{doacaoId}/atualizar` | Reconciliação explícita limitada, autorizada e com CSRF; não recebe status do cliente |
| `POST /api/pagbank/webhooks/checkout` e `/pagamento` | Rotas REST públicas específicas, sem sessão/CSRF, com assinatura obrigatória |

O caminho fixo de doação fica separado de `/pagamentos/{matriculaId}` dos cursos. O GET legado `/pagamentos/doacao` pode apenas redirecionar para intenção da sessão ou para `/doacoes`; não usar flash de sucesso como fonte financeira. Remover/desativar a antiga aprovação simulada `POST /pagamentos/doacao/confirmar`, sem alterar a confirmação simulada de cursos nesta sprint.

Não confiar em `permitAll` como autorização de consulta. Como a sessão local não é necessariamente um principal Spring Security, verificar acesso em `DoacaoService`/controller com as regras da seção 3.4; não exigir `.authenticated()` inadvertidamente de um visitante.

Adicionar CSRF seletivo aos POSTs de doação via Spring Security, mantendo compatibilidade com o restante do sistema; não ativar globalmente sem revisar formulários antigos. Incluir token Thymeleaf e testes, deixando apenas webhook fora desse controle. Aplicar limites de criação por sessão/IP na borda ou aplicação conforme hospedagem; reCAPTCHA pode ser avaliado se abuso real justificar, sem torná-lo dependência obrigatória acadêmica.

Não registrar token, assinatura, corpo financeiro bruto ou mensagem do doador em logs. Registrar IDs de correlação, operação, estado, código saneado e latência. Não fazer redirect para URL fornecida pelo doador; PAY deve ser HTTPS e host oficial validado por igualdade/lista explícita por ambiente. Configurar HTTPS/cookies adequados na hospedagem.

### 5.2 Transformação de UI — uma das etapas finais

- Preservar página inicial `/doacoes`, valores sugeridos, outro valor e mensagem opcional, com erros claros em pt-BR e possibilidade de corrigir sem perder os dados.
- **Substituir a apresentação de pagamento da doação por confirmação/detalhes da doação:** valor, destinatário, mensagem, caráter avulso, botão “Continuar para o PagBank” e explicação do redirecionamento.
- Remover desse fluxo todos os campos PAN/CVV/validade, método local, código/QR Pix, contador e botão “Já fiz o pagamento”. Os métodos e dados financeiros pertencem ao checkout hospedado.
- Resultado só mostra “Doação recebida”/agradecimento de recebimento quando estado persistido permite. Pendente/análise tem texto próprio e botão atualizar com intervalo mínimo; voltar cedo não é erro nem aprovação.
- Cancelar/voltar **no navegador** não altera automaticamente o estado financeiro. Não prometer que fechar a página cancela instrumento emitido. Cancelamento administrativo/reembolso exige operação efetiva no provedor.
- Disponibilizar “Voltar às doações” e “Conhecer os cursos” como navegação, **sem vínculo entre doar e acessar curso**.
- Sem sessão recuperável, exibir explicação neutra sem informações da intenção. Não criar checkout em GET/reload, nem obrigar a aba a permanecer aberta para processar pagamento.
- Cursos mantêm template/JS legados separados; testes de regressão verificam que a separação não os converteu em pagamentos reais ou quebrou sua renderização.

## 6. Pré-requisitos do desenvolvedor e Sandbox

### 6.1 O que pode ser feito sem credenciais

Implementar e testar domínio, validação de valor, idempotência local, autorização de visitante, telas, cliente HTTP contra servidor simulado, assinatura com token fictício e recuperação de falhas. Isso não prova PAY funcional, assinatura do provedor nem habilitação da conta.

### 6.2 O que exige Sandbox

- [ ] Acessar `https://portaldev.pagbank.com.br/`, concluir cadastro/autenticação e obter token na aba **Tokens** [8, 9]. Não incluir credenciais no repositório ou fixtures.
- [ ] Confirmar que o token pode criar Checkout para a conta de teste e quais métodos estão disponíveis. A documentação oficial indica conta PagBank e login do portal por CPF/CNPJ; **não há base para garantir Sandbox anônimo ou independente de conta PagBank**.
- [ ] Se não houver acesso, confirmar com suporte se é necessário criar/migrar tipo de conta para acessar Sandbox. O material público não distingue perfeitamente exigência comercial de produção do cadastro de desenvolvimento. Não há evidência de que seja preciso já receber dinheiro real para começar os testes.
- [ ] Preparar Java 21, wrapper Maven e MongoDB isolado de desenvolvimento/teste; verificar conexão e validadores reais. Não criar matrícula/curso para poder doar.
- [ ] Configurar as variáveis já necessárias para iniciar a aplicação (`MAIL_HOST/PORT/USER/PASSWORD`, `GOOGLE_CLIENT_ID/SECRET`, `CLOUDINARY_URL`, `MONGO_URI`), evitando esses serviços nos testes unitários.
- [ ] Preparar HTTPS público para webhooks por deploy de teste ou túnel, com certificado válido, POST JSON, header e corpo preservados, sem login/captcha na borda. `localhost` não é acessível pelo PagBank.
- [ ] Usar o domínio público também para a navegação do doador: cookie da sessão de localhost não acompanha automaticamente o domínio do túnel. Testar retorno sem sessão de forma controlada.
- [ ] Configurar URLs curtas, dentro dos limites da API; ao mudar túnel, atualizar base e criar novos checkouts, pois os antigos guardam URLs anteriores. Manter app/job/túnel ativos para eventos atrasados.
- [ ] Criar prova curta com doação fictícia, PAY hospedado, pagamento simulado, ambos os webhooks e consultas autenticadas. Guardar fixtures saneadas para fechar DTOs, associação, assinatura e hosts válidos.
- [ ] Confirmar suporte de idempotência do Checkout e comportamento de múltiplas tentativas/expiração; na falta de confirmação aplicar bloqueio conservador em resultado incerto.

Configuração proposta: `PAGBANK_TOKEN`, `PAGBANK_ENVIRONMENT=sandbox`, `APP_PUBLIC_BASE_URL` e propriedades de timeout/expiração. Mapear ambiente a base fixa permitida; não aceitar host de API no navegador. Token só no backend/secret store/ambiente local ignorado; nunca HTML, JS, logs, Docker ou arquivo versionado. Não commitar `.zed/`. Ausência de credencial causa indisponibilidade clara de criação, **não fallback de doação aprovada simulada**.

### 6.3 Limitações e cobertura do Sandbox

Sandbox não movimenta dinheiro real [7, 13]. É suficiente para demonstração acadêmica do fluxo simulado, mas não comprova recebimento em produção, taxas, antifraude real ou liquidação.

- Cartões oficiais permitem cenários de aprovação/recusa [14]; a página lista recursos Order. Confirmar aplicação dos dados na UI hospedada do Checkout antes de prometer cobertura. Digitar somente dados fictícios na página do PagBank; não construir formulário local para contornar limitação.
- Simulador [15]: Pix até R$100 pago imediato; acima de R$100 até R$200 pago com atraso de 5 minutos; acima de R$200 até R$300 aguardando; acima de R$300 até R$400 recusado; acima de R$400 aguardando. Usar **valores de doação de teste**, não preço de curso.
- Boleto: até R$100 pago imediato; acima de R$100 até R$200 pago em 5 minutos; acima de R$200 até R$300 cancelado em 5 minutos; acima de R$300 até R$400 cancelado imediato [15]. Confirmar aplicabilidade dos gatilhos ao Checkout hospedado na prova.
- A exclusão de “API Pix” do simulador se refere à API própria, não é justificativa para trocar o Checkout por Pix Bacen. Não foi comprovada cobertura integral de débito, carteiras, análise de risco, estorno parcial, chargeback ou todos os atrasos na UI hospedada.
- Repetição, ordem inversa, queda de processo e estados não reproduzíveis no portal devem ter testes automatizados com fixtures, identificados como testes locais, não como E2E PagBank.

### 6.4 Requisitos separados de produção

- [ ] Definir titular e confirmar conta Vendedor/Empresarial habilitada para receber pela integração; o guia prevê migração de PF para Vendedor, então CNPJ não é requisito universal comprovado [10].
- [ ] Confirmar com PagBank adequação da conta/atividade à arrecadação descrita como doações. A API é um checkout comercial; nomear item como “Doação” não comprova habilitação institucional, isenção ou benefício fiscal. Não prometer recibo dedutível.
- [ ] Solicitar homologação obrigatória para integração direta [16]. Material informa até quatro dias úteis com logs corretos, não uma garantia de go-live.
- [ ] Obter token de produção em **Vendas → Integrações → Gerar Token** [8], isolado do Sandbox.
- [ ] Configurar domínio definitivo, segredos, monitoramento de pendências e responsável por atendimento/reembolso/duplicidade. Estorno automático não está no MVP; procedimento manual deve ser acordado antes de arrecadar dinheiro real.
- [ ] Validar mínimos/máximos por método, taxas e eventual parcelamento; fazer teste real controlado somente com autorização do titular, após habilitação. Este plano não autoriza operação real.
- [ ] Garantir que telas simuladas de cursos não sejam divulgadas como cobrança real pronta. A conclusão de doações não equivale a certificar todos os fluxos financeiros do projeto.

## 7. Plano de implementação ordenado

As tarefas abaixo são futuras; este documento não implementa a funcionalidade.

### Etapa 1 — fechar contrato e decisões de doação

1. Confirmar limites R$1–R$10.000, mensagem privada até 500 caracteres, doação pública sem login, crédito em uma parcela e titular único. Cursos/recorrência continuam fora do escopo, sem nova discussão de cobrança inicial nesta sprint.
2. Preparar Sandbox/HTTPS e executar prova de criação mínima, PAY, eventos e consultas conforme seção 6. Registrar fixtures sem segredos e resolver formato dos pagamentos, associação de referências, dados mínimos de comprador, assinatura e idempotência externa.

### Etapa 2 — domínio, acesso e persistência

3. Criar `Pagamento` com finalidade doação, dados embutidos, estados financeiros e tentativas, `PagamentoRepository` e view models. Valor em centavos exatos, usuário opcional; sem modelo/repository de curso novo.
4. Entregar migração revisável de coleção, índices e validador. Como não existe coleção financeira no código, verificar se há estrutura real instalada; não assumir que o dicionário foi aplicado ou sobrescrever dados de usuário. Testar ObjectId/datas/centavos com o Spring Data efetivo.
5. Criar `DoacaoService`: validação, chave de submissão, criação única/snapshot, autorização de visitante/usuário vinculado e conjunto de intenções por sessão. Ajustar GET `/doacoes` existente para emitir chave sem duplicar mapping. Testar duas abas e retries do mesmo formulário.
6. Adicionar operações CAS/lock e transições por charge no serviço financeiro, inclusive resultado de criação incerto. A primeira versão não precisa transações multidocumento nem replica set só para esse fluxo, pois intenção e pagamento estão juntos.

### Etapa 3 — integração PagBank reutilizável

7. Criar propriedades, `RestClient` dedicado com timeouts e DTOs em `dto/pagbank/`, respeitando APIs/imports do Boot 4. Implementar criar/consultar/paginar/inativar e pedidos/charges. Sem SDK JS, WebFlux, dependência de hash ou interfaces genéricas desnecessárias.
8. Implementar criação/reúso em `PagamentoService`: tentativa antes da rede, resposta antes do redirect, validação de PAY, limites de criação, política de timeout e nenhuma repetição cega de POST. `DoacaoService` fornece snapshot autorizado; cliente não conhece doação/matrícula.
9. Trocar backend da doação simulada por rotas de confirmação/checkout persistidas, proteger POSTs com CSRF seletivo e desativar a aprovação antiga de doação por `metodo`. Não remover globalmente `app.pagamento.simulado` nem alterar fluxo de matrícula. Mensagens de falha pt-BR com flash/model.

### Etapa 4 — confirmação e recuperação

10. Criar endpoints REST públicos específicos e autenticidade sobre bytes originais, com limites de corpo/tempo. Validar checkout e pedido/charges sem sessão e sem seguir URLs arbitrárias; aplicar estados com CAS.
11. Implementar job de reconciliação em lotes, retomada após reinício, erros transitórios e revisão financeira. Reutilizar mesma operação no webhook e na atualização explícita do resultado. Garantir que callback não dependa de `usuarioLogadoId` nem dispare qualquer operação de matrícula.
12. Implementar telas/rotas de retorno com autorização da intenção, estado local, atualização limitada e tratamento neutro de sessão perdida. Nunca aprovar por query string ou flash; preservar dinheiro recebido mesmo em situação inesperada.

### Etapa 5 — uma das etapas finais: página de doações funcionando

13. Criar/aplicar templates próprios `doacao-confirmacao.html` e `doacao-resultado.html`, reaproveitando layout/estilos/ilustração. Transformar a tela de pagamento **da doação** em resumo de valor/mensagem/destinatário e botão para PagBank; nenhum formulário financeiro local.
14. Ajustar `doacoes.html` e `doacoes.js`: erros por valor/mensagem, teto/precisão coerentes, submissão sem dependência exclusiva de JS, chave da intenção, botão/feedback de envio e preservação de dados na validação. GET/reload não cria checkout.
15. Retirar referências ao JS de cartão/Pix na doação; não apagar os arquivos ainda usados pela simulação de cursos/galeria. Atualizar links/rotas antigas sem colisão com `/pagamentos/{matriculaId}`. Testar navegação de visitante e usuário logado.

### Etapa 6 — testes e entrega

16. Adicionar testes JUnit 5/Mockito/Spring MVC/cliente HTTP simulado e persistência com banco isolado, usando starters por módulo do Boot 4 já presentes. Não copiar `spring-boot-starter-test` de receita antiga nem usar token real em CI.
17. Cobrir: formatos monetários, limites, mensagem/escape, envio sem JS, doações intencionais repetidas, duas abas, duplo clique, sessão perdida/trocada, CSRF, ID de terceiro, webhook sem sessão, assinatura alterada/ausente, acentos/quebras de linha, eventos repetidos/fora de ordem, charge de outra doação, valor/moeda divergente, estorno, timeout e falha após persistência.
18. Executar `./mvnw test` com ambiente adequado; o `contextLoads` existente exige configuração externa e Mongo, não representa cobertura financeira. Testes unitários e HTTP simulado devem rodar sem PagBank. Testar corrida de criação/reconciliação com persistência real, não só mocks.
19. Validar E2E Sandbox: doador visitante e logado, doação paga/recusada/pendente, retorno antes do evento, expiração, replay e ausência de retorno; conferir UI e Mongo. Registrar exatamente o observado e o que foi coberto somente por simulação local.
20. Atualizar README de operação e dicionário efetivo de pagamentos/doações, índices, regras de privacidade e procedimento de recuperação/reembolso manual. Verificar regressão de renderização dos cursos; não mudar regras de matrícula/acesso. Revisar segredos/logs e checklist abaixo antes de considerar homologação.

## 8. Critérios de aceitação e pendências

### 8.1 Critérios verificáveis

- [ ] Visitante e usuário logado conseguem selecionar valor/mensagem, confirmar, seguir PAY e acompanhar doação sem precisar de matrícula, papel ALUNO ou conta local obrigatória.
- [ ] Página aceita valores sugeridos/outro dentro dos limites, rejeita entradas inválidas sem arredondamento oculto, preserva dados na correção e funciona sem depender exclusivamente do JavaScript.
- [ ] Intenção persiste antes da API; doação não desaparece ao fechar aba/encerrar sessão. Usuário, valor e mensagem não são alterados por parâmetros de retorno ou confirmação adulterada.
- [ ] Duplo envio da mesma chave usa a mesma intenção; dois POSTs de checkout não geram criações simultâneas. Nova doação intencional é permitida. Abas distintas não sobrescrevem intenções abertas.
- [ ] Confirmação/resultado de uma intenção não podem ser lidos/alterados por outra sessão apenas conhecendo ID. Sessão perdida recebe informação genérica; usuário vinculado pode acompanhar após autenticação própria.
- [ ] Nenhum formulário próprio de cartão/Pix ou botão “Já fiz o pagamento” aparece no fluxo de doações. `POST /pagamentos/doacao/confirmar` antigo não aprova nem produz sucesso simulado.
- [ ] Retorno e `ACTIVE`/`AUTHORIZED`/`WAITING`/`IN_ANALYSIS` nunca exibem recebimento confirmado. Só `PAID` associado, consultado e com montantes válidos confirma a doação.
- [ ] Webhooks funcionam sem sessão e rejeitam assinatura ausente/inválida sem mutação. Repetição/ordem inversa não duplica recebimento nem deixa recusa antiga apagar charge paga.
- [ ] Timeout de criação não dispara novo POST incerto; falhas transitórias/reinício/webhook perdido são retomáveis ou entram em atendimento explícito, não em falso sucesso.
- [ ] Expirar/inativar checkout não invalida automaticamente instrumento emitido; novas tentativas não se sobrepõem sem prova de encerramento. Recebimentos duplicados/estornos/divergências são preservados para atendimento.
- [ ] Cliente/DTOs/assinatura/reconciliação não dependem de matrícula, curso, aprovação do professor ou sessão. Doar não concede acesso nem altera vaga/matrícula.
- [ ] Telas/backend simulados de cursos continuam separados e não são convertidos silenciosamente em integração real. Nenhuma recorrência foi adicionada.
- [ ] Segredos fora do repositório/log/UI, métodos escolhidos no PagBank, testes automatizados passando e evidências Sandbox saneadas documentadas.

### 8.2 Decisões e riscos ainda dependentes do responsável/ambiente

| Prioridade | Ponto | Ação |
| --- | --- | --- |
| Bloqueante do ensaio externo | Conta/PortalDev/token Sandbox e domínio HTTPS | Obter acesso e URL pública; não presumir Sandbox anônimo |
| Bloqueante do contrato | Lista real de pagamentos de checkout, propagação de referência e assinatura | Executar prova curta e guardar fixtures saneadas antes de concluir DTOs/reconciliação |
| Alto | Idempotência de criação externa não documentada especificamente | Confirmar com provedor; até lá bloquear recriação automática em resultado desconhecido |
| Alto | Intenção pública sem login e retorno por outro domínio | Implementar posse por sessão/usuário vinculado; aceitar limite do MVP para recuperação de visitante sem sessão e testar cookies do domínio público |
| Alto | Pix/boleto pendente além da validade do checkout | Reconciliar instrumentos antes de permitir outra tentativa; jamais prometer cancelamento só por voltar à loja |
| Alto para produção | Titular, atividade de doações, taxas e atendimento de duplicidade/estorno | Confirmar habilitação comercial e responsável; recibo fiscal/dedutibilidade e estorno automático fora do escopo |
| Médio | Limites atuais e crédito em uma parcela | Responsável aprovar R$1–R$10.000, mensagem privada e uma parcela; validar limites do método/conta |
| Médio | Doação recusada, sessão perdida ou criação incerta | Aprovar mensagens e procedimento de atendimento; recuperação por e-mail/link secreto só se solicitada em escopo próprio |
| Médio | Reúso versus overengineering | Entregar núcleo técnico sem handlers fictícios de cursos; regras de mensalidade ficam na sprint futura |
| Médio | Template e flag compartilhados | Separar apenas doação; não apagar assets compartilhados nem alterar política de simulação de cursos |

**Ações suas antes de começar:** confirmar os limites/condições propostas; obter acesso Sandbox/token; providenciar HTTPS público; definir conta recebedora e contato responsável por atendimento. O escopo **doação agora, cursos depois** já está definido nesta revisão e não depende de nova decisão sobre cobrança inicial de cursos.

## 9. Referências oficiais

1. [Checkout e Link de Pagamento](https://developer.pagbank.com.br/docs/checkout) — fluxo hospedado, PAY, meios e endpoints.
2. [Criar Checkout](https://developer.pagbank.com.br/reference/criar-checkout) — Bearer, payload, valores, expiração e URLs distintas.
3. [Consultar Checkout](https://developer.pagbank.com.br/reference/consultar-checkout) — consulta/paginação; exemplo incompleto dos pagamentos.
4. [Webhooks de Checkout](https://developer.pagbank.com.br/reference/webhooks-checkout) — estados e payloads CHEC/ORDE/CHAR.
5. [Confirmar autenticidade da notificação](https://developer.pagbank.com.br/reference/confirmar-autenticidade-da-notificacao) — SHA-256, corpo original e header.
6. [Consultar pedido](https://developer.pagbank.com.br/reference/consultar-pedido) e [Consultar pagamento](https://developer.pagbank.com.br/reference/consultar-pagamento) — evidências de pedido/charge.
7. [Ambientes disponíveis](https://developer.pagbank.com.br/docs/ambientes-disponiveis) — bases e ausência de movimentação real no Sandbox.
8. [Token de autenticação](https://developer.pagbank.com.br/docs/token-de-autenticacao) — obtenção Sandbox e produção.
9. [Crie sua conta PagBank](https://developer.pagbank.com.br/docs/crie-sua-conta-pagbank) e [Portal do Desenvolvedor](https://portaldev.pagbank.com.br/) — cadastro/acesso; área autenticada não validada nesta análise.
10. [Primeiros passos do PagBank](https://developer.pagbank.com.br/docs/primeiros-passos-pagbank) — conta Vendedor/Empresarial e migração PF.
11. [Objeto Checkout](https://developer.pagbank.com.br/reference/objeto-checkout) — enums, dados pessoais, débito e limites.
12. [Chaves públicas e de idempotência](https://developer.pagbank.com.br/docs/chaves-publicas-e-de-idempotencia) — conceitos gerais, sem garantia específica de idempotência do Checkout confirmada aqui.
13. [Testar integração](https://developer.pagbank.com.br/docs/testar-integracao) — cenários e reenvio de notificações.
14. [Cartões de teste](https://developer.pagbank.com.br/docs/cartoes-de-teste) — dados fictícios e escopo descrito em Orders.
15. [Simulador](https://developer.pagbank.com.br/docs/simulador) — Pix/boleto, valores e atrasos.
16. [Solicitar homologação](https://developer.pagbank.com.br/docs/solicitar-homologacao) — obrigatoriedade, formulário e prazo informado.

Regras específicas de doação, nomes de classes/campos/rotas e parâmetros de operação acima são **propostas locais**, não recursos nem garantias inventados da API. Dúvidas de conta, contrato e comportamento Sandbox precisam de prova ou confirmação oficial antes de operação real.
