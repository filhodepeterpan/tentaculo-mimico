# 🐙 Tentáculo Mímico — Sistema de Gestão de Cursos

> Sistema web desenvolvido como Projeto de Extensão do 3º ciclo do curso de **Desenvolvimento de Software Multiplataforma (DSM)** da **FATEC Praia Grande**.

---

## 📖 Sobre o Projeto
A **Tentáculo Mímico** é uma Organização Não Governamental (ONG) sem fins lucrativos e centro cultural localizado no bairro Ribeirópolis, em Praia Grande/SP. O espaço oferece atividades artísticas e de bem-estar a preços populares (como teatro, violão, canto e yoga), além de atuar como mídia independente na cobertura de eventos e produção de entrevistas com artistas e educadores da periferia.

O sistema foi idealizado para centralizar e automatizar a gestão operacional da ONG — contemplando o controle de cadastros, turmas, matrículas e pagamentos mensais —, proporcionando uma experiência fluida tanto para a gestão quanto para os alunos e professores.

---

## 👥 Equipe do Projeto

| Integrante | Papel Principal | Funções e Responsabilidades |
| :--- | :--- | :--- |
| **Daniela Feitoza Alves** | Scrum Master & Back-end | Gestão ágil e desenvolvimento da lógica de servidores. |
| **Douglas Soares Santos** | Product Owner & Arquiteto de Dados | Modelagem NoSQL, documentação e apoio ao Front-end. |
| **Eduardo Soares e Araujo** | Desenvolvedor Full-stack | Codificação de rotinas e integração entre interfaces e regras de negócio. |
| **Livia Gabrieli de Albuquerque Guedes** | Desenvolvedora Front-end | Construção das telas e suporte ao design de interfaces. |
| **Vitória Millena Meireles da Silva** | Desenvolvedora Back-end | Implementação de serviços back-end e suporte ao banco de dados. |

---

## ⚙️ Engenharia do Sistema & Arquitetura

O projeto adota uma arquitetura **MPA (Multi-Page Application)**, acoplando o Front-end e o Back-end em uma única aplicação para reduzir complexidades desnecessárias.

### 🛠️ Tecnologias Utilizadas
* **Back-end & Servidor:** Java 21, Spring Boot (v4.1.1)
* **Template Engine:** Thymeleaf (v3.1.5)
* **Banco de Dados:** MongoDB (v8.3) NoSQL, hospedado via **MongoDB Atlas**
* **Hospedagem da Aplicação:** Render (PaaS)
* **Gerenciamento de Mídia:** Cloudinary (v1.39.0)
* **Front-end:** HTML5, CSS3, JavaScript (ES6)
* **Acessibilidade:** Widgets integrados do **VLibras** e **UserWay**
* **Ferramentas de Apoio:** Figma (Prototipagem e Wireframes), Lucidchart (Modelagem de Dados), Trello (Kanban / Metodologias Ágeis), Git & GitHub

---

## 🗄️ Modelagem do Banco de Dados (NoSQL)

O banco de dados foi estruturado em coleções otimizadas no MongoDB:

1. **`usuarios`**: Unifica os perfis de `aluno`, `professor` e `administrador` por meio de um discriminador (`tipo_usuario`). Suporta autenticação local (com hash de senha) e integração via Google OAuth2.
2. **`cursos`**: Armazena as informações das oficinas e aulas ministradas, incluindo grade de horários embutida (`horarios`), vagas totais/disponíveis e controle atômico de capacidade.
3. **`matriculas`**: Controla o vínculo entre alunos e cursos, registrando o ciclo de estados (pendente, aceita, ativa, cancelada, recusada) e a liberação de acesso.
4. **`pagamentos`**: Gerencia as cobranças mensais vinculadas às matrículas ativas, histórico de tentativas de transações externas e status financeiros.
5. **`webhooks_processados`**: Garante a idempotência das notificações assíncronas recebidas de gateways de pagamento externos, evitando processamentos duplicados. Conta com índice TTL para expiração automática de logs temporários em 30 dias.

---

## 🚀 Como Executar o Projeto Localmente

### Pré-requisitos
Certifique-se de ter instalado em sua máquina:
* **Java Development Kit (JDK) 21**
* **Maven** (ou utilize o wrapper embutido do Spring Boot)
* Instância do **MongoDB** rodando localmente ou string de conexão ativa com o **MongoDB Atlas**

### Passo a Passo

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/seu-usuario/tentaculo-mimico.git
   cd tentaculo-mimico
   ```

2. **Configure as variáveis de ambiente / propriedades:**
   No arquivo `src/main/resources/application.properties` (ou `.yml`), configure a sua string de conexão com o MongoDB e credenciais de serviços (como Cloudinary e APIs de pagamento):
   ```properties
   spring.data.mongodb.uri=sua_string_de_conexao_mongodb

   # Cloudinary
   cloudinary.cloud-name=seu_cloud_name
   cloudinary.api-key=sua_api_key
   cloudinary.api-secret=seu_api_secret

   # Google OAuth2
   spring.security.oauth2.client.registration.google.client-id=seu_client_id
   spring.security.oauth2.client.registration.google.client-secret=seu_client_secret

   # Gateway de pagamento
   pagamento.api.token=seu_token_de_pagamento
   ```
   > ⚠️ **Atenção:** nunca faça commit de credenciais reais. Utilize variáveis de ambiente ou um arquivo local ignorado pelo Git.

3. **Compile e execute o projeto:**
   ```bash
   ./mvnw clean spring-boot:run
   ```

4. **Acesse no navegador:**
   Abra o link gerado (geralmente `http://localhost:8080`).

---

## 📌 Licença
Este projeto foi desenvolvido para fins acadêmicos e de extensão comunitária em parceria com a ONG Tentáculo Mímico e a FATEC Praia Grande.
