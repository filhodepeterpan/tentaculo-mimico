# Tentáculo Mímico
## Sistema de Gestão de Cursos de um Centro Cultural

- Em desenvolvimento...

## Doações (Checkout PagBank)

O visitante escolhe um valor entre R$ 1,00 e R$ 10.000,00 em `/doacoes`. O servidor cria um Checkout
e responde com 303 para a página hospedada do PagBank. O token nunca vai para o navegador.
Defina no ambiente do servidor (não em arquivos versionados):

- `PAGBANK_TOKEN`: token da conta no ambiente selecionado; sem ele, a doação mostra erro.
- `PAGBANK_ENVIRONMENT`: `sandbox` (padrão) ou `production`. A API usa apenas o host oficial desse ambiente.
- `APP_PUBLIC_BASE_URL`: origem de retorno do navegador, sem caminho. Em produção, HTTPS público
  (ex.: `https://exemplo.org`); no Sandbox também é permitido `http://localhost:8080` ou
  `http://127.0.0.1:8080` pela validação local, mas **a criação real do Checkout foi bloqueada
  pela Cloudflare do PagBank para ambos os hosts de loopback**.
  `redirect_url` e `return_url` apontam para `${APP_PUBLIC_BASE_URL}/doacoes?retorno=pagbank`.

As variáveis já necessárias para iniciar o restante da aplicação continuam obrigatórias (mail, Google,
Cloudinary e MongoDB); a integração de doação não escreve no Mongo. O POST público `/doacoes` exige
token CSRF da página. Não há retry automático de criação de checkout. A mensagem opcional foi retirada
da interface: não existe destino nem armazenamento para dedicatórias nesta versão. O Checkout expira em
duas horas, e o crédito foi limitado a uma parcela; os demais meios dependem da conta PagBank.

O retorno configurado no PagBank exibe **apenas um agradecimento visual**, inclusive quando alguém usa
“voltar à loja” sem pagar. Não prova recebimento: o marcador da URL pode ser digitado manualmente.
O botão Voltar do navegador pode retornar à página anterior sem esse marcador, portanto sem modal;
fechar a aba no PagBank também não exibe modal. Conferir recebimentos, reembolsos e eventuais cobranças
duplicadas é responsabilidade do titular no PagBank. A aplicação não mantém histórico, consulta status
ou recebe webhooks. Não reenviar após falha de rede sem antes conferir uma eventual página já aberta.

Antes de liberar produção, testar no Sandbox com token próprio, retorno HTTPS alcançável e meios fictícios:
confirmar a resposta PAY e os hosts usados, omissão de `customer`/`payment_methods`, fim do fluxo,
“voltar à loja” e comportamento do modal (teclado, foco, rolagem e telas pequenas). Depois confirmar
habilitação da conta, taxas e homologação com o PagBank. O Bootstrap 5.3.3 (licença MIT em
`static/js/vendor/bootstrap/LICENSE`) é carregado só na página de doações; os estilos do modal são
isolados em `static/css/pages/doacoes.css` e não alteram os modais/cursos existentes.

O retorno é uma navegação do navegador, não um webhook: não há endpoint de callback servidor-servidor
para expor. Em prova direta de `POST /checkouts` no Sandbox, o payload completo foi aceito com retorno
HTTPS público (`201` e PAY em `pagamento.sandbox.pagbank.com.br`); requisições equivalentes com
`localhost` ou `127.0.0.1` nas URLs de retorno receberam página de bloqueio da Cloudflare (`403`).
Para ensaiar o retorno ao site local, use um endereço HTTPS público temporário apontando para a
aplicação (túnel) ou um deploy de teste. A liberação de HTTP no código nunca se aplica à produção.

Se o POST `/doacoes` retornar à página com erro, o código HTTP visto no navegador é o da aplicação:
o PagBank é chamado pelo servidor. O log `Falha ao criar checkout PagBank` mostra o ambiente, HTTP
externo e, quando disponível, somente o código saneado de `error_messages[].error`. Em caso de 403,
verifique se `PAGBANK_ENVIRONMENT=sandbox` usa o token Sandbox (valor puro, sem prefixo `Bearer `)
e consulte o código na [tabela oficial de erros](https://developer.pagbank.com.br/reference/codigos-de-erro-checkout).
Não envie HAR, cookies, JWT, token, body de resposta ou headers de autenticação em chamados públicos.
