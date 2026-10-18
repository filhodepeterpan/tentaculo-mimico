# PR — Doações: Checkout hospedado, sem persistência

## Novas chaves para execução do projeto
- `PAGBANK_TOKEN` - Token utilizado para se comunicar com a conta correta do PAGBANK
- `APP_PUBLIC_BASE_URL` - URL utilizada para a aplicação identificar qual URL o PAGBANK deveria usar para redirecionar o usuário
  - Em produção, precisa ser https://tentaculo-mimico.onrender.com/, ou o domínio da tentáculo se comprarmos um
  - No ambiente de dev, eu estou deixando https://google.com mesmo

## Resumo

- O botão **Doar** cria um Checkout no backend e envia o visitante diretamente à página hospedada do PagBank (303). Valor validado no servidor (R$ 1,00 a R$ 10.000,00, centavos exatos), CSRF só no POST `/doacoes`, token de API somente no servidor. Erros voltam à página de doações com aviso.
- Foi removida a simulação **da doação**, inclusive confirmação falsa e passagem por `pagamento.html`. O fluxo de cursos, seus assets e sua simulação continuam intactos. Mensagem/dedicatória removida da interface porque não seria armazenada nem entregue.

## Jornada visível

1. Visitante escolhe valor em `/doacoes` e clica **Doar**. O botão fica indisponível durante o envio para reduzir cliques acidentais; isso **não** garante criação única do checkout.
2. O backend recebe POST com CSRF, valida o valor e chama `POST /checkouts` com item técnico, expiração de duas horas, crédito em uma parcela e URLs de retorno confiáveis. Extrai somente o link HTTPS `PAY` de host oficial e responde 303. Os meios exibidos dependem da conta/dispositivo; nenhum dado de cartão ou Pix é coletado pelo site.
3. A pessoa interage na página do PagBank. O redirecionamento de finalização (`redirect_url`) e a opção de **voltar à loja** (`return_url`) apontam para `/doacoes?retorno=pagbank`.
4. Somente quando essa URL é acessada, a página mostra o modal Bootstrap **“Agradecemos sua generosidade!”** com área reservada para imagem e texto de agradecimento. O marcador é retirado da URL após a abertura; um acesso normal a `/doacoes` não abre o modal.

**Voltar sem pagar:** clicar em “voltar à loja” no PagBank pode carregar o retorno configurado e abrir o agradecimento, mesmo sem pagamento. Já o botão **Voltar do navegador** percorre o histórico e pode levar diretamente à página anterior (por exemplo, `/doacoes` sem marcador), sem abrir o modal; o histórico exato varia com os redirecionamentos e o navegador. Nenhum desses caminhos verifica transação.

**O modal não confirma recebimento, aprovação ou pagamento.** O marcador é público e pode ser digitado manualmente. Se a pessoa fechar a página/aba no PagBank sem retornar ao site, **não haverá modal**, independentemente do que ocorreu financeiramente. O titular confere o recebimento no PagBank.
