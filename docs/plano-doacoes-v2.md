# Doações v2 — redirecionamento ao PagBank, sem persistência

**Status:** plano de implementação, não implementação. **Referências reconferidas:** 18/10/2026.

Este é o plano vigente para o **escopo reduzido** solicitado. Substitui, para esta sprint, as propostas de `plano-doacoes-checkout-pagbank.md`, preservado como histórico. Suas exigências de entidade financeira, coleção, índices, confirmação intermediária, webhooks e reconciliação **não se aplicam a esta versão**.

## 1. Escopo acordado

Fluxo único:

```text
doacoes.html → clicar em Doar → backend cria checkout
            → página hospedada do PagBank
            → /doacoes → modal Bootstrap de agradecimento
```

- Doações avulsas para uma única conta PagBank, com valor escolhido na página atual.
- Visitantes podem doar; não exigir login, matrícula ou papel de aluno.
- **Sem criar ou modificar entidades, coleções, documentos, índices ou schemas no banco.** Não registrar pagamentos em usuários, matrículas ou outra coleção existente como contorno da restrição.
- Sem tela de confirmação, resumo intermediário ou página local de resultado financeiro.
- Sem formulários locais de cartão/Pix: o PagBank apresenta e processa os métodos disponíveis.
- Sem webhook, job, consulta de status, histórico, total arrecadado, recibo, estorno automático ou associação persistente de usuário/doação.
- Cursos, recorrência, vagas e acesso a aulas continuam fora da sprint.
- O registro financeiro e sua conferência ficam no **PagBank**, não no sistema local. A conta recebedora deve ter um responsável por essa operação.

### Significado do modal

O modal é **um agradecimento pela participação no fluxo**, não uma confirmação financeira. O retorno do navegador pode acontecer com pagamento pendente, após voltar à loja ou sem pagamento concluído. Esta versão não distingue esses estados.

Não adicionar “Pagamento aprovado”, “Doação recebida”, comprovante ou valor pago ao modal. Não executar qualquer regra financeira ou de acesso a partir de parâmetro de retorno. A página do PagBank e a conta do recebedor são as fontes de informação financeira nesta sprint.

Esse limite permite atender ao fluxo visual solicitado sem fingir que o sistema verificou o recebimento. Se futuramente for obrigatório agradecer **somente após captura confirmada**, será outro requisito, com consulta autenticada/correlação e revisão do escopo.

## 2. Diagnóstico do que será alterado

| Arquivo real | Estado atual | Alteração planejada |
| --- | --- | --- |
| `src/main/resources/templates/doacoes.html` | Valores sugeridos, campo “Outro”, mensagem opcional e POST `/doacoes` | Manter formulário como entrada; acrescentar modal Bootstrap no próprio template ou fragmento dedicado |
| `src/main/resources/static/js/pages/doacoes.js` | Copia valor/mensagem para campos ocultos e valida parte do valor | Manter seleção/validação; bloquear duplo clique durante envio e abrir modal no retorno |
| `src/main/resources/static/css/pages/doacoes.css` | Importa CSS global e estiliza a página | Estilizar modal e placeholder sem redesenhar o site |
| `src/main/java/br/com/tentaculomimico/controller/DoacaoController.java` | POST guarda `DoacaoPendente` em sessão e envia para `/pagamentos/doacao`; outra rota simula confirmação | POST passa a validar, chamar serviço e redirecionar diretamente ao PAY; retirar simulação e tela intermediária **da doação** |
| `src/main/java/br/com/tentaculomimico/controller/PaginasController.java` | Já possui GET `/doacoes` | Reutilizar GET existente, sem criar mapping duplicado; disponibilizar sinal de retorno para renderização |
| `src/main/java/br/com/tentaculomimico/security/SecurityConfig.java` | Doações públicas; CSRF globalmente desabilitado | Preservar acesso público; proteger o POST de doação sem ativação global que quebre outras páginas |
| `src/main/resources/templates/pagamento.html` e `static/js/pages/pagamento.js` | Compartilhados por doações e pagamentos simulados de cursos | Doação deixa de passar por eles; não apagar recursos ainda utilizados por cursos |
| `templates/fragments/modal.html`, `static/css/componentes/modal.css` | Modal próprio, não Bootstrap; usa classe `.modal` | Não reutilizar como se fosse Bootstrap nem migrar todos os modais nesta sprint |

Não foram encontradas referências a Bootstrap em `src/`. `doacoes.css` importa `global.css`, que já importa o CSS do modal próprio: **a classe `.modal` conflita com a do Bootstrap**. A integração visual precisa tratar esse ponto explicitamente.

O backend atual valida R$1–R$10.000 e limita mensagem a 500 caracteres; seu parser arredonda valores com `HALF_UP`. Reaproveitar os limites como padrão inicial, mas converter centavos exatamente e rejeitar precisão inválida, sem confiar exclusivamente em JS.

## 3. Escolha da API: Checkout hospedado

### Decisão proposta

Usar **Checkout API**, `POST /checkouts`, com um item técnico “Doação ao Tentáculo Mímico”. Isso **não exige criar pedido, entidade ou registro no nosso banco**. `items` é um requisito do payload do PagBank, não uma decisão de modelagem local.

O guia oficial também descreve Link de Pagamento sem necessidade de e-commerce [1]. Portanto, não ter pedido local não inviabiliza Checkout.

### Sobre “Transactions”

A ausência de pedido local, por si só, não determina qual API serve. O requisito decisivo é **obter uma página hospedada com meios de pagamento e retorno ao site**. A documentação atual de Order descreve criação/cobrança de pedidos, charges, Pix, cartão etc., mas não deve ser confundida com uma URL PAY de Checkout [3].

Não foi confirmado nesta revisão um contrato específico de API chamado “Transactions” que substitua esse fluxo hospedado. Se esse nome se refere a uma API legada ou outro produto contratado, é necessário fornecer/confirmar sua documentação, autenticação e suporte a redirecionamento. Não presumir incompatibilidade absoluta, mas também não inventar endpoint ou garantia.

**Para não bloquear nem ampliar a sprint, o plano executável adota Checkout**, cujo contrato hospedado está confirmado [1, 2]. Não implementar duas APIs ou uma camada de seleção entre elas.

### Contrato mínimo

- Sandbox: `https://sandbox.api.pagseguro.com/checkouts`.
- Produção: `https://api.pagseguro.com/checkouts`.
- Método POST, JSON e header `Authorization: Bearer <token>` exclusivamente no backend.
- Exemplo estrutural para R$35, com URLs geradas pelo servidor:

```json
{
  "reference_id": "<UUID-efemera-da-requisicao>",
  "expiration_date": "<ISO-8601-com-fuso>",
  "customer_modifiable": true,
  "items": [{
    "name": "Doação ao Tentáculo Mímico",
    "quantity": 1,
    "unit_amount": 3500
  }],
  "redirect_url": "https://<dominio>/doacoes?retorno=pagbank",
  "return_url": "https://<dominio>/doacoes?retorno=pagbank"
}
```

- `reference_id` é uma referência efêmera, não ID de documento e não garantia de idempotência externa. Não persistir UUID/checkout em banco.
- `unit_amount` vem do valor validado no servidor, em centavos inteiros exatos. Nome do item e URLs não são controlados pelo formulário.
- Omitir `shipping`, frete, descontos, adicionais, recorrência e URLs de notificação. Não criar webhook apenas para responder 200 e descartar evento.
- Deixar o PagBank coletar os dados necessários com `customer_modifiable=true`; confirmar na prova Sandbox a omissão de `customer`. Não inventar CPF/telefone nem exigir cadastro local por esse motivo.
- Expiração explícita proposta: duas horas. Evita depender da divergência entre guia e referência sobre validade padrão.
- Ler a URL do objeto de `links` cujo `rel` seja **`PAY`**, validar HTTPS/host oficial e responder com **303 See Other** para essa URL. Não construir link manualmente, nem redirecionar o navegador a uma URL de API JSON.
- O formulário não oferece métodos de pagamento próprios. Preferir os habilitados pela conta no Checkout; confirmar comportamento de omitir `payment_methods` em Sandbox. Débito tem habilitação prévia; não prometer carteiras/métodos indisponíveis na conta/dispositivo.
- Propõe-se crédito em uma parcela para simplificar; se necessário, configurar `INSTALLMENTS_LIMIT=1` conforme contrato. Não implementar parcelamento/juros localmente.

## 4. Arquitetura mínima e reúso futuro

Manter o fluxo idiomático controller → service → cliente HTTP, **sem repository**:

| Componente | Responsabilidade |
| --- | --- |
| `DoacaoController` existente | Receber POST público, tratar erro pt-BR e devolver redirect |
| `DoacaoService` novo | Validar valor/mensagem e montar solicitação da doação; retornar URL PAY |
| `PagBankClient` novo | Chamar `POST /checkouts`, interpretar resposta/erros e extrair PAY; não conhece matrícula ou sessão |
| `PagBankProperties` + bean `RestClient` | Token, ambiente/base permitida, URL pública, expiração e timeouts |
| DTOs records pequenos em `dto/pagbank/` | Contrato JSON de criação/resposta, com campos realmente utilizados |

Usar Spring Boot **4.1.1**, Java 21 e `RestClient` com as dependências existentes. Não introduzir SDK JS de cartão, WebFlux, interface genérica de gateway, estratégia por produto, `PagamentoService` com máquina de estados ou entidade `Pagamento`.

O reúso futuro está em configuração/cliente HTTP/DTOs. A sprint de cursos poderá acrescentar persistência, consulta, webhook e consequências no domínio **quando houver autorização**, sem acoplar essas responsabilidades ao serviço de doação atual.

### Limites conscientes de não persistir

- Não há trilha financeira local, deduplicação durável, reconciliação após reinício, recuperação por ID ou acompanhamento de estado. Não recriar essas funcionalidades em memória como um banco paralelo.
- Desabilitar botão durante envio reduz cliques acidentais, mas **não garante exatamente uma criação/cobrança**. Uma pessoa pode gerar mais de um checkout por abas/reenvio. Não apresentar bloqueio de botão como idempotência financeira.
- Não fazer retry automático de POST após timeout/resultado incerto. Exibir erro e orientar a verificar eventual página já aberta antes de tentar novamente; não afirmar que nada foi criado. O suporte de idempotência específico de Checkout não foi confirmado pela referência usada.
- Não armazenar histórico financeiro em `HttpSession`, arquivos, logs ou propriedades de usuário como substituto do banco. Sessão pode continuar existindo normalmente para CSRF/UI da aplicação, sem entidade de pagamento.
- O app não precisa receber webhook nem manter URL pública de callback servidor-servidor. Precisa de URL de retorno do navegador válida no ambiente de teste/produção.

## 5. Retorno e modal Bootstrap

### Gatilho simples

Configurar `redirect_url` e `return_url` para `/doacoes?retorno=pagbank`. No GET já existente, disponibilizar um booleano de apresentação se o marcador corresponder ao valor esperado; renderizar/abrir modal somente nesse retorno. Um visitante que acessa `/doacoes` normalmente não vê o modal.

O marcador **não autentica PagBank nem comprova pagamento** e pode ser digitado manualmente. Isso é aceitável porque só controla um agradecimento genérico, sem dados privados, mutações ou privilégios. Não chamá-lo `pagamentoAprovado`/`doacaoConcluida` nem tratar `status=PAID` vindo do navegador como verdade.

Após abrir, remover apenas o marcador da URL com `history.replaceState`, para não reabrir no reload normal nem compartilhar uma URL que sempre agradece. Não fazer nova chamada ao PagBank no retorno. Se o usuário fechar a aba no PagBank, não haverá retorno nem modal — e isso não desfaz eventual pagamento.

### Conteúdo solicitado, nesta ordem

**Título:**

> Agradecemos sua generosidade!

**Imagem:**

> [Placeholder de imagem]()

Reservar uma área visual para a imagem, sem `<img src="">` quebrada. O arquivo atual `static/img/doacoes-icon.png` pode preencher essa área se aprovado; até lá, manter placeholder explícito, sem inventar arte externa.

**Texto:**

> Sua contribuição ajuda o Tentáculo Mímico a continuar criando, ensinando e levando a arte para mais pessoas e espaços. Obrigado por acreditar no nosso trabalho e por fazer parte dessa história.

Não inserir valor, confirmação bancária, resumo de compra ou texto de curso. Oferecer fechar pelo botão com rótulo pt-BR e por Escape. Manter foco, navegação por teclado, identificação acessível do título e comportamento responsivo.

### Bootstrap no projeto existente

- Criar fragmento dedicado, por exemplo `templates/fragments/modal-agradecimento-doacao.html`, com markup Bootstrap (`modal-dialog`, `modal-content`, `data-bs-dismiss`, título e corpo). Não modificar o fragmento genérico usado nas matrículas.
- Incluir Bootstrap 5.3.x de versão fixada **apenas na página de doações**, com CSS/JS necessários ao modal. Preferir arquivos estáticos locais com licença; não exigir npm/build novo. Se usar CDN, fixar versão e integridade e documentar a dependência de rede.
- Evitar carregar Bootstrap globalmente ou fazer o reset da biblioteca alterar o site inteiro. Isolar estilos do modal/backdrop ao componente dedicado e testar colisões de `.modal` com `componentes/modal.css`; não resolver alterando todos os modais da aplicação.
- Usar o plugin Bootstrap para abrir/fechar (`Modal.getOrCreateInstance(...).show()`), não um modal próprio apenas com nome Bootstrap. Não usar `hidden` permanentemente no elemento que o plugin precisa exibir.
- Testar também os elementos/modais existentes no footer e a ordem dos estilos, pois a página já importa CSS global. Fechar o agradecimento não pode bloquear rolagem/foco da página.

## 6. Tratamento de formulário e erros

- Manter inicialmente os limites atuais de R$1–R$10.000, sujeitos aos meios/conta realmente habilitados; rejeitar valor ausente, negativo, fora de faixa ou com precisão maior que dois centavos. Usar `BigDecimal`, sem `double`/arredondamento silencioso no servidor.
- Aproveitar os valores sugeridos e “Outro” de `doacoes.html`. Erro de validação ou da API volta à mesma página com mensagem clara; não abre modal de agradecimento nem redireciona para a simulação.
- O campo de mensagem atual não ganha armazenamento nesta sprint. **Não prometer que a instituição receberá/guardará a dedicatória.** Validar tamanho se continuar sendo enviado, não logar/não enviar ao PagBank sem finalidade/contrato confirmado. Decisão de UI pendente: retirar o campo nesta entrega ou mantê-lo com explicação explícita de que não será salvo; não criar persistência para justificá-lo.
- Proteger POST `/doacoes` com CSRF seletivo usando Spring Security e token Thymeleaf; preservar doação pública e não ativar proteção global sem revisar formulários antigos.
- Credencial ausente, 401/403, 4xx de validação, 5xx, timeout, corpo inválido ou resposta sem PAY devem resultar em indisponibilidade/erro pt-BR, nunca falso sucesso. Não exibir resposta bruta/token.
- Base API e URL de retorno vêm de configuração confiável. Não aceitar do cliente URL de retorno, nome do item ou token. Validar PAY contra hosts HTTPS oficiais do ambiente observados na prova; não usar `contains("pagbank")`.
- Logar somente operação, código saneado e diagnóstico técnico mínimo. Sem token, corpo de cartão, CPF, mensagem pessoal ou arquivo de histórico de doações.

## 7. Pré-requisitos e prova externa curta

1. Obter acesso ao [Portal do Desenvolvedor](https://portaldev.pagbank.com.br/) e token Sandbox na aba Tokens [4]. O Sandbox não envolve recebimento real, mas exige cadastro/autenticação; não garantir acesso sem conta PagBank. Se o tipo de conta bloquear acesso, confirmar com suporte.
2. Configurar token exclusivamente no backend (`PAGBANK_TOKEN`), ambiente (`PAGBANK_ENVIRONMENT=sandbox`) e base pública de retorno (`APP_PUBLIC_BASE_URL`). Mapear ambiente para host permitido. Não versionar segredos nem `.zed/`.
3. Usar Java 21/wrapper Maven e variáveis já exigidas para iniciar o projeto. O restante da aplicação ainda usa MongoDB, mas a nova integração **não escreve pagamentos nele** e não exige migração.
4. Criar um checkout de teste para doação, omitir notificações e dados de cliente conforme contrato mínimo, conferir PAY e retorno. Usar HTTPS de teste/deploy ou túnel para facilitar navegação real; **não há requisito de túnel para webhook**, pois webhook não será implementado. Confirmar URLs aceitas pelo provedor no ambiente.
5. Testar o modal tanto após finalizar a interação no PagBank quanto ao usar “voltar à loja”; o agradecimento não é uma afirmação de aprovação. Usar somente meios/dados fictícios oficiais no Sandbox.
6. Em produção: titular deve ter conta recebedora habilitada, confirmar adequação da atividade de doações, métodos, taxas e homologação da integração direta [5]. Obter token de produção separado. Conferência de recebimentos/reembolsos será manual no PagBank; não prometer recibo fiscal ou benefício tributário.

## 8. Tarefas de implementação

1. **Validar o contrato mínimo em Sandbox:** criação sem persistência/webhook, resposta PAY, hosts e retorno. Não abrir investigação de estados financeiros que esta sprint não usa.
2. Criar `PagBankProperties`, `RestClient`, DTOs mínimos e `PagBankClient`; configurar token por ambiente, timeouts e ausência de retry automático de POST.
3. Criar `DoacaoService` com validação e criação de checkout, sem repository, entidade financeira, chamada a matrícula ou dependência de sessão financeira.
4. Alterar POST `/doacoes` existente: validar → chamar serviço → 303 para PAY. Remover desse fluxo o armazenamento de `DoacaoPendente`, QR simulado e `app.pagamento.simulado` como autorização de sucesso. Não alterar a flag globalmente, pois cursos ainda a utilizam.
5. Desativar/remover POST `/pagamentos/doacao/confirmar` de simulação. Fazer o GET legado `/pagamentos/doacao` voltar a `/doacoes`, se necessário para compatibilidade. Não encaminhar a doação para `pagamento.html`.
6. Adaptar GET `/doacoes` em `PaginasController` para apresentar o marcador de retorno, sem duplicar rota nem consultar pagamento. Configurar ambas as URLs de volta para essa página.
7. Acrescentar modal Bootstrap dedicado com título, placeholder e texto exatos da seção 5; carregar recursos restritos à página, tratar conflitos de CSS e adicionar abertura/limpeza do marcador em `doacoes.js`.
8. Ajustar formulário, feedback de envio e erros, resolver o campo mensagem e adicionar CSRF seletivo. Preservar templates/assets de cursos e outros consumidores.
9. Testar validação e contrato HTTP com mocks/servidor simulado, controller e retorno com testes MVC; confirmar ausência de chamadas de escrita financeira em Mongo. Usar JUnit 5 e starters por módulo do Boot 4 existentes, sem SDK/framework novo.
10. Executar roteiro manual Sandbox + responsividade/acessibilidade do modal; registrar configuração e limites do fluxo no README durante a implementação. Nenhum segredo, migração ou commit é autorizado apenas por este plano.

## 9. Critérios de aceitação

- [ ] Clique em “Doar”, com valor válido, leva diretamente ao PagBank depois do POST; não há tela de confirmação local.
- [ ] Valor inválido/erro de criação mantém o usuário em doações com erro claro e sem agradecimento falso.
- [ ] Pagamento é feito exclusivamente na página hospedada; nenhum campo de cartão/Pix local é usado pela doação.
- [ ] Retorno final é `/doacoes`, com modal Bootstrap contendo título, área de imagem e texto solicitados, sem declaração de aprovação financeira.
- [ ] GET normal de `/doacoes` não abre modal; retorno abre e limpa marcador sem iniciar novo checkout. Fechar/Escape/foco/scroll e telas pequenas funcionam.
- [ ] Nenhuma entidade, coleção, migração ou escrita de pagamento/doação é adicionada, inclusive em coleções existentes, arquivos ou sessão financeira paralela.
- [ ] Não há webhook, job, máquina de estados, histórico, consulta de status ou callback de matrícula escondido no escopo.
- [ ] O fluxo funciona sem login; token só no backend; URLs/valor são validados, POST protegido e falha não retorna para a confirmação simulada.
- [ ] Não há promessa de idempotência durável, confirmação de pagamento ou recuperação após reinício que a solução não entrega.
- [ ] Código técnico de cliente/configuração fica reutilizável sem antecipar cursos; a simulação de cursos e os modais existentes não sofrem regressão.

## 10. Pendências e limites aceitos

- **Imagem:** há placeholder solicitado; confirmar se `doacoes-icon.png` deve preenchê-lo ou se outra arte será fornecida.
- **Mensagem opcional atual:** decidir remoção ou explicação de não retenção; não criar banco, log ou integração adicional para guardá-la.
- **Bootstrap:** inclusão de dependência frontend é necessária para o modal pedido e deve ser local à página, sem migração visual global.
- **Transactions:** alternativa não comprovada para página hospedada nesta análise. Se houver API específica contratada/documentada, pode ser reavaliada; Checkout já resolve sem pedido no banco.
- **Financeiro:** pendência, recusa, cancelamento, expiração e estorno não são acompanhados localmente. Recebedor acompanha no PagBank. Mesmo marcador de retorno pode ser acessado sem pagamento; só há efeito visual genérico.
- **Duplicidade/falha de rede:** sem persistência e garantia externa confirmada não existe deduplicação durável; evitar retries cegos e não prometer o contrário.
- **Produção:** depende de conta/token/habilitação/homologação e responsável operacional, não de acrescentar entidades não autorizadas nesta sprint.

## Referências oficiais

1. [Checkout e Link de Pagamento](https://developer.pagbank.com.br/docs/checkout) — página hospedada, PAY, retorno e link sem e-commerce.
2. [Criar Checkout](https://developer.pagbank.com.br/reference/criar-checkout) — POST, Bearer, item, centavos, retorno e notificações opcionais.
3. [Pedidos e pagamentos (Order)](https://developer.pagbank.com.br/docs/pedidos-e-pagamentos-order) — distingue operações de cobrança da solução hospedada; não comprova uma alternativa “Transactions” de redirecionamento.
4. [Token de autenticação](https://developer.pagbank.com.br/docs/token-de-autenticacao) e [Ambientes disponíveis](https://developer.pagbank.com.br/docs/ambientes-disponiveis).
5. [Solicitar homologação](https://developer.pagbank.com.br/docs/solicitar-homologacao).
6. [Modal Bootstrap 5.3](https://getbootstrap.com/docs/5.3/components/modal/) — markup, plugin, abertura, fechamento e acessibilidade.
