# Agent notes

## Project and commands

- Single Maven app: Spring Boot 4.1.1, Java 21, package `br.com.tentaculomimico`; entrypoint `src/main/java/br/com/tentaculomimico/TentaculoMimicoApplication.java`.
- `./mvnw spring-boot:run` runs locally; `./mvnw test` runs tests; focus tests with `./mvnw -Dtest=DoacaoServiceTest test` (replace class as needed). `./mvnw clean package -DskipTests` is the Docker build command.
- For persistence-only verification, `./mvnw -Dtest=CursoMapeamentoTest test` exercises Mongo field/type converters without a running database. `TentaculoMimicoApplicationTests` loads the Spring context and needs external configuration; local Mongo Compose config is in ignored `.zed/compose.yml`.
- Git tracks `src/`, `docs/`, the root Maven/Docker files, and this file; `.zed/` (local configuration, including credentials) and `target/` are ignored. Never copy secrets or build outputs into tracked files.

## Preserve the existing system

- This codebase has uneven/unfinished areas. Prefer small, local changes over broad cleanup or architectural rewrites; avoid moving/renaming code unless the task requires it, so a future structural reorganization remains possible.
- Separate responsibilities by semantics, not the current package names: **data** = `@Document` entities in `model/`, Mongo repositories in `repository/`, mapping converters in `config/`; **domain** = state/invariants (e.g. `StatusMatricula.ocupaVaga()`) and workflows/authorization in `service/`; **UI/transport** = `controller/`, request/response `dto/`, `model/view/`, Thymeleaf templates and static assets. Some response-only classes also live in `model/`; do not treat them as Mongo entities or put persistence behavior in them.
- Main flow is `controller` → `service` → `repository`, but some controllers currently read repositories directly. Keep HTTP binding, redirects, and template model assembly in controllers; put new domain rules and writes in services rather than copying that exception.
- UI uses server-rendered Thymeleaf (`src/main/resources/templates/fragments/` holds reusable components). User-facing text and existing domain errors are in Brazilian Portuguese; keep presentation-specific formatting out of persisted fields.
- Mongo field names/types are not inferred consistently: inspect `@Field` and `MongoConfig` before changing a document; `CursoMapeamentoTest` guards snake_case, ObjectId, date/time conversions. `docs/data_dictionary.md` and `docs/ddl_banco/` describe the database contract—coordinate schema changes rather than casually renaming fields.
- `docs/requisitos.md` records product rules and IDs (`RN...`); consult it when changing domain behavior, but verify implementation in code because requirements can describe unimplemented features.

## Important behavior and configuration

- Spring Security permits many application routes; controllers/services also enforce access via `SessaoService`. Add explicit session and role checks to new protected routes; do not assume URL authorization alone protects them.
- Course enrollment payment confirmation is still simulated by default (`app.pagamento.simulado=true`); do not treat the UI confirmation as a real payment. Donations use the PagBank client (`PAGBANK_TOKEN`, `PAGBANK_ENVIRONMENT`, `APP_PUBLIC_BASE_URL`), so distinguish that flow from simulated enrollment payments.
- `application.properties` requires mail (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASSWORD`), Google OAuth (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) and `MONGO_URI`; `CloudinaryConfig` also requires `CLOUDINARY_URL`. The configured `spring.mongodb.uri` is the **Boot 4** property (not the old `spring.data.mongodb.uri`). Do not assume integrations are available without their environment configuration.
