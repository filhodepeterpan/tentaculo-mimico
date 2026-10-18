# Tentáculo Mímico
## Sistema de Gestão de Cursos de um Centro Cultural

- Em desenvolvimento...

## Doações (Checkout PagBank)

O visitante escolhe um valor entre R$ 1,00 e R$ 10.000,00 em `/doacoes`. O servidor cria um Checkout
e responde com 303 para a página hospedada do PagBank. O token nunca vai para o navegador.
Defina no ambiente do servidor (não em arquivos versionados):

- `PAGBANK_TOKEN`: token da conta no ambiente selecionado; sem ele, a doação mostra erro.
- `PAGBANK_ENVIRONMENT`: `sandbox` (padrão) ou `production`. A API usa apenas o host oficial desse ambiente.
- `APP_PUBLIC_BASE_URL`: origem HTTPS pública do site, sem caminho (ex.: `https://exemplo.org`).
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

Antes de liberar produção, testar no Sandbox com token próprio, retorno HTTPS válido e meios fictícios:
confirmar a resposta PAY e os hosts usados, omissão de `customer`/`payment_methods`, fim do fluxo,
“voltar à loja” e comportamento do modal (teclado, foco, rolagem e telas pequenas). Depois confirmar
habilitação da conta, taxas e homologação com o PagBank. O Bootstrap 5.3.3 (licença MIT em
`static/js/vendor/bootstrap/LICENSE`) é carregado só na página de doações; os estilos do modal são
isolados em `static/css/pages/doacoes.css` e não alteram os modais/cursos existentes.
