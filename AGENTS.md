# AGENTS.md — Tentáculo Mímico

Sistema de gestão de cursos de um centro cultural: **Spring Boot 4.1.1 / Java 21**
(server-side Thymeleaf) + MongoDB. Todo texto de UI, comentários e mensagens de
erro são em **português (pt-BR)** — mantenha isso. Pacote raiz:
`br.com.tentaculomimico`.

Atenção ao stack: é Boot 4, não 3. O `pom.xml` usa `spring-boot-starter-webmvc`
(não `spring-boot-starter-web`) e **não declara `spring-boot-starter-test`**: os
starters de teste vêm por módulo (`spring-boot-starter-webmvc-test`,
`spring-boot-starter-data-mongodb-test`, `...-thymeleaf-test`,
`...-validation-test`). Não copie receitas de Boot 3 sem conferir.

## Comandos

- Rodar: `./mvnw spring-boot:run` (usa o `mvnw`; Maven 3.9.16 via wrapper).
- Build do jar: `./mvnw clean package -DskipTests` (é o que o `Dockerfile` faz).
- Testes: `./mvnw test`; um só: `./mvnw -Dtest=TentaculoMimicoApplicationTests test`.
- Docker: `docker build -t tentaculo-mimico .` (build multi-stage, EXPOSE 8080).

Rodar exige as variáveis `MAIL_HOST/PORT/USER/PASSWORD`, `GOOGLE_CLIENT_ID/SECRET`,
`CLOUDINARY_URL` e `MONGO_URI` (ver Configuração). O script `./run` e o
`compose.yml` locais ficam em `.zed/`, que é **gitignored** — nunca commite `.zed/`
(nem qualquer um desses segredos).

## Arquitetura

Fluxo esperado: `controller` → `service` → `repository`. Controllers chamam
services para regra de negócio; repositories (Spring Data `MongoRepository`) só
acessam o banco.

- `model/` — documentos Mongo e enums de domínio. Enums carregam regra
  (`StatusMatricula.ocupaVaga()/emAberto()/chave()`, `StatusCurso`).
- `model/view/` — view models para templates (`AlunoPendenteView`, etc.).
- `dto/` — records de request (`LoginRequestDTO`, `CadastroRequestDTO`); os campos
  são `String` de propósito e a conversão/validação acontece no service.
- `service/` — use cases. Erros de negócio são `RuntimeException` com mensagem
  pt-BR que vai direto para a tela. Cadastro/Perfil também têm exceções com erros
  por campo (`CadastroInvalidoException`, `PerfilService.DadosInvalidosException`).
- `security/` — `SecurityConfig`, `JwtTokenProvider`, `OAuth2LoginSucessoHandler`.
- `config/` — `CloudinaryConfig` (bean único).

Exceções ao fluxo: `CursoController`, `PerfilController`, `PagamentoController` e
`AdminController` acessam repositories direto (leitura para montar o model).
Isso é o estado atual; ao adicionar regra de negócio, prefira o service.

## Documentação de domínio (`docs/`)

São a fonte de verdade **de intenção** do produto (não do estado atual) e estão
fora do git até serem commitados.

- `docs/requisitos.md` — RF01–RF26, RNF01–RNF07 e regras de negócio
  RN001–RN059. Os IDs `RN0xx` aparecem em comentários de código e PRs; ao mexer
  numa regra, consulte o texto dela e mantenha o ID citado.
- `docs/data_dictionary.md` — modelo Mongo **alvo**, campo a campo, com a RN que
  justifica cada um e as decisões de embed vs. reference.

O código implementa só um subconjunto do que os docs descrevem. Antes de assumir
que algo existe, confira o código:

- Os docs citam as collections `pagamentos` e `webhooks_processados`; **nenhuma
  das duas tem model/repository**. Pagamento é simulado (ver seções abaixo).
- Não implementados apesar de documentados: confirmação de e-mail (RN010),
  cobrança mensal (RN047/RN052), suspensão/restauração de acesso por
  inadimplência (RN053/RN055/RN058), reembolso (RN036/RN059) e as notificações
  automáticas (RNF07).
- Divergência de nome: o dicionário escreve `aceito_aguardando_pagamento`, mas o
  código e o `$jsonSchema` de `matriculas` usam
  `aceita_aguardando_pagamento` (ver `StatusMatricula.chave()`). O schema do
  banco manda — não "corrija" o código pelo doc.

## Modelo de autenticação (importante)

A autenticação real é a **sessão HTTP** (`SessaoService`, chave de sessão
`usuarioLogadoId`), não o JWT.

- O `Spring Security` está permitindo praticamente todas as rotas em
  `SecurityConfig` (`permitAll` amplo). A autorização é feita **manualmente** nos
  controllers/services via `SessaoService.usuarioLogado()`/`exigirUsuarioLogado()`
  e checagens de `TipoUsuario` (ex.: `AdminController.exigirAdministrador`,
  `AdminService` + `PaginasController.paginaDeAdmin`). Ao criar rota nova, você
  **deve** adicionar a checagem você mesmo — não conte com o `SecurityConfig`.
- O cookie `jwt` (`httpOnly`) é gerado no login/cadastro/OAuth, mas **nenhum
  filtro o valida** — `JwtTokenProvider` só gera. Trate-o como legado.
- Login Google é via OAuth2 (`/oauth2/authorization/google`), tratado em
  `OAuth2LoginSucessoHandler`; conta com senha local não é vinculada ao Google
  automaticamente.

## Persistência (MongoDB)

- Coleções implementadas: `usuarios`, `cursos`, `matriculas` (o
  `docs/data_dictionary.md` ainda descreve `pagamentos` e `webhooks_processados`,
  que não existem no código). Os documentos seguem um `$jsonSchema` do banco,
  então **não renomeie campos** sem ajustar o schema.
- Mapeamento de nomes **não é uniforme**: `Usuario`/`Autenticacao`/`Matricula`
  usam `@Field` explícito com `snake_case` (`tipo_usuario`, `data_nascimento`,
  `aluno_id`...); `Curso` **não tem `@Field` nenhum**, então persiste com o nome
  Java em camelCase (`professorId`, `vagasTotais`, `dataInicio`...). Não assuma
  uma estratégia global de nomes — olhe a anotação de cada campo.
- `Matricula.status` é gravado como **texto** (`"pendente"`,
  `"aceita_aguardando_pagamento"`, `"ativa"`, `"recusada"`, `"cancelada"`), não
  como enum; a conversão fica em `getStatusMatricula()/setStatusMatricula()`
  (getter marcado `@Transient`). Referências como `curso_id`/`aluno_id` são
  `ObjectId` (armazenadas como String).
- Consultas derivadas ficam no repository (`findByProfessorId`, `findByAlunoId`,
  `findByCursoIdIn`...). Prefira métodos derivados a strings de query.

## Convenções

- Controllers: `@Controller` para Thymeleaf, `@RestController` só para JSON em
  `/api/**` (`AdminController`). Erros de fluxo normalmente são capturados com
  `catch (RuntimeException e)` e colocados no `Model` (`erroGeral`, `erroX`) ou
  via `RedirectAttributes` flash — siga esse estilo em vez de deixar 500 subir.
- Injeção por construtor; `@RequiredArgsConstructor` (Lombok) é usado em várias
  classes, mas `Curso`, `Matricula` e `Horario` têm getters/setters manuais.
- `GlobalControllerAdvice` expõe `usuarioLogado` em todo model. Templates decidem
  a UI por `tipoUsuarioLogado` (minúsculo: `aluno`/`professor`/`administrador`).
- Código morto conhecido: `AlunoLogadoProvider/AlunoLogadoMock` e
  `ProfessorLogadoProvider/ProfessorLogadoMock` são resquícios de teste;
  `CursoService` ainda injeta `ProfessorLogadoProvider` mas não o usa. Não
  propague esse padrão de mock — use `SessaoService`.

## Frontend

- Thymeleaf em `src/main/resources/templates/`; fragmentos reutilizáveis em
  `templates/fragments/` (`inputs.html`, `modal.html`, `card*.html`, ...). A rota
  `/componentes` renderiza `exemplos-componentes.html`, uma galeria dos
  fragmentos — bom ponto de partida para UI.
- CSS/JS em `static/css` e `static/js`, divididos em `componentes/` e `pages/`.
  Siga essa divisão por página/componente ao criar arquivos novos.

## Funcionalidades incompletas (não assuma que funcionam)

- **Pagamento/doação são simulados**: não há gateway. Só funcionam com
  `app.pagamento.simulado=true` (padrão). `PagamentoController` e
  `DoacaoController` traem o clique do usuário; em produção trocar o POST por
  webhook do gateway (`MatriculaService.confirmarPagamento`). Há `TODO(gateway)`s.
- `/confirmar-email` é só um template: a validação do token de confirmação de
  e-mail ainda não foi implementada (`PaginasController.confirmarEmail`).

## Configuração

- `application.properties` usa placeholders de ambiente. **Atenção:**
  `spring.mongodb.uri=${MONGO_URI}` está com a chave errada — o Spring Boot lê
  `spring.data.mongodb.uri`. Do jeito que está, `MONGO_URI` é ignorado e a app cai
  no padrão `localhost:27017`. Use a chave correta ao mexer nisso.
- `CLOUDINARY_URL` é lido via `@Value` em `CloudinaryConfig`.
- `app.pagamento.simulado` controla a confirmação de pagamento (ver acima).
- `spring.thymeleaf.cache=false` (conveniência de dev) está no properties
  principal, não em um perfil.

## Testes

- Existe só `TentaculoMimicoApplicationTests` (`@SpringBootTest contextLoads`).
  Não há `application-test.properties` nem Mongo embarcado: o teste precisa de um
  MongoDB acessível (o `compose.yml` em `.zed/` sobe um em `localhost:27017`).
- Não há suíte de integração. Ao adicionar testes, siga JUnit 5 / Spring Boot Test
  já presentes — não introduza framework novo sem necessidade.

## Git / PRs

- Repositório: `github.com/filhodepeterpan/tentaculo-mimico` (`origin`, SSH).
- Branches de feature seguem `feature/TASK-<n>-<descricao>` e vêm de PRs
  (`Merge pull request #<n> ...`). IDs de regra de negócio (`RN0xx`) e de tarefa
  (`TASK-xx`) aparecem em comentários de código — mantenha-os ao editar.
