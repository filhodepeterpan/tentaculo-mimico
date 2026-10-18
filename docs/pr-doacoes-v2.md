# PR — Doações v2: Checkout hospedado, sem persistência

## Resumo para revisão do PO

- O botão **Doar** cria um Checkout no backend e envia o visitante diretamente à página hospedada do PagBank (303). Valor validado no servidor (R$ 1,00 a R$ 10.000,00, centavos exatos), CSRF só no POST `/doacoes`, token de API somente no servidor. Erros voltam à página de doações com aviso.
- Foi removida a simulação **da doação**, inclusive confirmação falsa e passagem por `pagamento.html`. O fluxo de cursos, seus assets e sua simulação continuam intactos. Mensagem/dedicatória removida da interface porque não seria armazenada nem entregue.
- **Não** foram criados registros financeiros, entidades, repositories, coleções, índices, migrações, webhooks, consultas de status, jobs, histórico, estornos, telas intermediárias ou página local de resultado. O PagBank é o único lugar de acompanhamento financeiro; não há deduplicação durável nem retry automático.

## Jornada visível

1. Visitante escolhe valor em `/doacoes` e clica **Doar**. O botão fica indisponível durante o envio para reduzir cliques acidentais; isso **não** garante criação única do checkout.
2. O backend recebe POST com CSRF, valida o valor e chama `POST /checkouts` com item técnico, expiração de duas horas, crédito em uma parcela e URLs de retorno confiáveis. Extrai somente o link HTTPS `PAY` de host oficial e responde 303. Os meios exibidos dependem da conta/dispositivo; nenhum dado de cartão ou Pix é coletado pelo site.
3. A pessoa interage na página do PagBank. O redirecionamento de finalização (`redirect_url`) e a opção de **voltar à loja** (`return_url`) apontam para `/doacoes?retorno=pagbank`.
4. Somente quando essa URL é acessada, a página mostra o modal Bootstrap **“Agradecemos sua generosidade!”** com área reservada para imagem e texto de agradecimento. O marcador é retirado da URL após a abertura; um acesso normal a `/doacoes` não abre o modal.

**Voltar sem pagar:** clicar em “voltar à loja” no PagBank pode carregar o retorno configurado e abrir o agradecimento, mesmo sem pagamento. Já o botão **Voltar do navegador** percorre o histórico e pode levar diretamente à página anterior (por exemplo, `/doacoes` sem marcador), sem abrir o modal; o histórico exato varia com os redirecionamentos e o navegador. Nenhum desses caminhos verifica transação.

**O modal não confirma recebimento, aprovação ou pagamento.** O marcador é público e pode ser digitado manualmente. Se a pessoa fechar a página/aba no PagBank sem retornar ao site, **não haverá modal**, independentemente do que ocorreu financeiramente. O titular confere o recebimento no PagBank.

## Testes e pendências

- `./mvnw clean package` com valores fictícios nas variáveis exigidas pela aplicação: **18 testes passaram, compilação e empacotamento passaram**. Cobertura: serviço/centavos e limites, retorno localhost só no Sandbox, contrato HTTP simulado, rejeição de PAY inseguro/ausente, erros da API, retorno, 303 público e CSRF seletivo por MVC. Sem essas variáveis, `./mvnw test` não inicia o teste de contexto preexistente (`MAIL_HOST` ausente); o teste foi reexecutado com ambiente de teste e passou.
- Smoke test local com Chromium headless: `/doacoes` ficou sem modal aberto; `/doacoes?retorno=pagbank` exibiu `.modal.show` e backdrop do plugin Bootstrap. Captura em viewport móvel (390 × 844) confirmou que o conteúdo e o botão Fechar cabem na tela. Esse teste **não** envolveu o PagBank nem substitui verificação interativa de acessibilidade.
- **Não foi feito ensaio ponta a ponta no Sandbox:** falta token/conta PagBank. O código aceita retorno `http://localhost:8080` apenas no Sandbox; a documentação pública não confirma se o provedor o aceita. Pendente conferir criação real sem `customer`/`payment_methods` e sem notificações, host PAY real do Sandbox, métodos da conta e ambos os retornos (finalizar e “voltar à loja”). Caso o PagBank rejeite localhost, usar URL HTTPS de teste. Hosts PAY aceitos pela implementação são explícitos e precisam ser reconferidos com resposta real; adaptar apenas após evidência do Sandbox.
- Pendente validação manual interativa em dispositivos do foco/Escape/rolagem, dos elementos do rodapé e da navegação de retorno; pendente decisão sobre a arte para substituir o placeholder. A operação em produção depende de token separado, habilitação da conta, taxas e homologação PagBank.
