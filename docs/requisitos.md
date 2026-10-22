## **2.4 Engenharia do Sistema** {#2.4-engenharia-do-sistema}

### **2.4.1 Requisitos Funcionais** {#2.4.1-requisitos-funcionais}

**1\. Autenticação e cadastro**

**RF01 — Cadastro de usuários**

O sistema deve permitir o cadastro de usuários como aluno ou professor, mediante o fornecimento dos dados obrigatórios: Nome, e-mail, CPF e data de nascimento.

Os campos “nome” e “e-mail” serão preenchidos automaticamente caso a sessão seja iniciada via Google OAuth2. Note que o campo “nome” será alterável, para não atrelar o nome da conta Google ao nome do usuário. O e-mail por sua vez será imutável pois é atrelado à conta Google.

O cadastro deve validar:

* formato do e-mail;  
* formato do CPF;  
* unicidade de e-mail e CPF;  
* confirmação do endereço de e-mail via código.

O cadastro de administradores deve ocorrer exclusivamente por meio interno do sistema.

**RF02 — Autenticação**

O sistema deve permitir que usuários cadastrados realizem login e acessem as funcionalidades de acordo com seu tipo de usuário e estado da conta.

Após um número determinado de tentativas consecutivas de login malsucedidas, a conta deve ser temporariamente bloqueada.

**RF03 — Recuperação de senha**

O sistema deve permitir que usuários recuperem o acesso à conta por meio de um fluxo de recuperação de senha.

**RF04 — Controle de acesso**

O sistema deve restringir funcionalidades e informações de acordo com o tipo de usuário autenticado.

O sistema deve distinguir, no mínimo, os seguintes perfis:

* aluno;  
* professor;  
* administrador.

Determinadas funcionalidades somente devem estar disponíveis após a confirmação do endereço de e-mail.

**2\. Gerenciamento de usuários**

**RF05 — Visualização de usuários**

O sistema deve permitir a visualização dos dados dos usuários de acordo com as permissões do perfil autenticado.

Alunos podem visualizar apenas dados não sensíveis dos professores.

Professores podem visualizar somente os dados dos alunos necessários para os cursos sob sua responsabilidade.

Administradores podem visualizar todos os dados dos usuários.

**RF06 — Atualização de Perfil**

O sistema deve permitir que usuários alterem seus próprios dados cadastrais.

O tipo de usuário não pode ser alterado pelo próprio usuário.

**RF07 — Gerenciamento administrativo de usuários**

O administrador deve poder alterar e excluir dados de outros usuários.

O administrador não pode excluir a própria conta.

**RF08 — Exclusão de conta**

O sistema deve permitir a exclusão de contas respeitando as condições estabelecidas para cada perfil.

Alunos somente podem excluir suas contas quando não possuem matrículas ativas.

Professores não podem excluir suas contas enquanto possuírem cursos disponíveis para matrícula ou cursos em andamento.

A exclusão de uma conta não deve eliminar informações históricas necessárias para preservar matrículas e pagamentos realizados.

**3\. Gerenciamento de cursos**

**RF09 — Cadastro e manutenção de cursos**

O professor deve poder criar e gerenciar múltiplos cursos.

Todo curso deve possuir:

* nome;  
* indicador de disponível ou indisponível;  
* data inicio;  
* data fim (opcional);  
* data de cadastro;  
* descrição;  
* valor;  
* horário;  
* duração;  
* número máximo de vagas;  
* número de vagas disponíveis  
* professor responsável.

**RF10 — Disponibilização de cursos**

O professor deve poder alterar a disponibilidade de um curso para novas matrículas.

Um curso somente pode ser disponibilizado quando todos os dados obrigatórios estiverem preenchidos e houver pelo menos uma vaga disponível.

Cursos indisponíveis não podem receber novas matrículas.

**RF11 — Gerenciamento de vagas**

O professor deve definir a quantidade máxima de vagas de cada curso.

O limite de vagas não pode ser reduzido para um número inferior à quantidade de alunos já matriculados.

Vagas liberadas pelo cancelamento de uma matrícula não devem ser disponibilizadas automaticamente. O professor deve decidir quando disponibilizá-las novamente.

**RF12 — Alteração de horários**

O sistema deve impedir que um professor disponibilize cursos cujos horários conflitem entre si.

**RF13 — Exclusão de cursos**

O professor deve poder excluir cursos que não possuam alunos matriculados.

Cursos que possuam matrículas não podem ser excluídos de forma que o histórico dessas matrículas seja perdido.

**4\. Matrículas**

**RF14 — Solicitação de matrícula**

O aluno deve poder solicitar matrícula em um ou mais cursos, desde que:

esteja autenticado;

o curso esteja disponível;

exista vaga;

não possua outra matrícula ativa para o mesmo curso;

não possua outra matrícula em curso com conflito de horário.

**RF15 — Aprovação de matrícula**

O professor responsável pelo curso deve poder aceitar ou recusar solicitações de matrícula.

Uma matrícula somente deve ser considerada efetivada após:

* aprovação pelo professor; e  
* confirmação do pagamento correspondente.

**RF16 — Gerenciamento de matrícula**

O sistema deve permitir o acompanhamento do estado de cada matrícula.

A matrícula deve possuir estados que permitam representar, no mínimo:

solicitação pendente;

* aprovada;  
* efetivada;  
* cancelada;  
* recusada;  
* suspensa.

O sistema deve registrar a data e o horário em que a matrícula foi realizada.

**RF17 — Cancelamento de matrícula**

O aluno deve poder cancelar sua matrícula.

O cancelamento deve preservar o registro histórico da matrícula, alterando seu estado para “cancelada”.

Após o cancelamento, o aluno não deve mais ser cobrado por aquela matrícula.

**RF18 — Reembolso de matrícula**

O sistema deve processar reembolsos quando o cancelamento ocorrer por iniciativa do curso ou do professor.

O cancelamento realizado pelo aluno não deve gerar reembolso.

**RF19 — Notificações de matrícula**

O sistema deve notificar:

o professor quando um novo aluno solicitar matrícula;

o aluno quando sua solicitação for aceita;

o aluno quando sua solicitação for recusada.

Alunos com matrícula recusada não devem ter acesso ao curso.

**RF20 — Cancelamento de curso**

Quando um professor tornar indisponível um curso antes de sua conclusão, o sistema deve:

* identificar os alunos matriculados;  
* notificá-los;  
* cancelar suas matrículas, quando aplicável;  
* processar os reembolsos correspondentes ao período não utilizado.

**5\. Pagamentos**

**RF21 — Cobrança de mensalidades**

O sistema deve gerar cobranças mensais enquanto uma matrícula permanecer ativa.

A cobrança somente deve ser iniciada após a aprovação da matrícula pelo professor.

**RF22 — Processamento de pagamentos**

O aluno deve poder realizar o pagamento das cobranças associadas às suas matrículas.

O sistema deve registrar o estado de cada pagamento, contemplando, no mínimo:

* pendente;  
* aprovado;  
* recusado;  
* vencido;  
* estornado.

**RF23 — Tentativa de pagamento**

Quando um pagamento for recusado, o sistema deve notificar o aluno e permitir uma nova tentativa de pagamento.

**RF24 — Controle de acesso baseado em pagamento**

O sistema deve verificar a situação do pagamento antes de liberar o acesso do aluno ao curso.

Quando uma cobrança não for paga até sua data de vencimento, o acesso ao curso deve ser suspenso automaticamente.

Após a confirmação de um pagamento em atraso, o acesso deve ser restaurado automaticamente.

**RF25 — Cancelamento e cobranças**

O sistema não deve gerar novas cobranças após o cancelamento da matrícula.

Quando um curso for cancelado pelo professor, o sistema deve realizar o reembolso dos pagamentos referentes ao período não utilizado.

**RF26 — Notificações de pagamento**

O sistema deve notificar o aluno sobre:

* cobranças próximas do vencimento;  
* cobranças vencidas;  
* pagamentos recusados;  
* alterações relevantes no estado de seus pagamentos.

### **2.4.2 Requisitos Não Funcionais** {#2.4.2-requisitos-não-funcionais}

**1\. Segurança**

**RNF01 — Armazenamento de senhas**

As senhas dos usuários devem ser armazenadas utilizando mecanismo de hash adequado, não podendo ser armazenadas em texto puro.

**RNF02 — Proteção de dados**

O sistema deve impedir que usuários tenham acesso a dados que não estejam autorizados a visualizar ou modificar.

**RNF03 — Dados de pagamento**

O sistema não deve armazenar dados sensíveis dos meios de pagamento.

2\. Integridade e consistência

**RNF04 — Integridade do histórico**

Operações de exclusão ou cancelamento não devem comprometer o histórico necessário para representar matrículas, pagamentos e demais registros financeiros.

**RNF05 — Consistência das vagas**

O sistema deve manter consistente a quantidade de vagas disponíveis em relação às matrículas existentes, impedindo que o limite de vagas seja ultrapassado.

**RNF06 — Consistência financeira**

O sistema deve manter o estado das matrículas e dos pagamentos consistente, especialmente durante aprovação de matrícula, cancelamento, suspensão de acesso e reembolso.

**3\. Automação**

**RNF07 — Processamento automático**

O sistema deve executar automaticamente processos decorrentes de eventos do domínio, incluindo:

* geração de cobranças mensais;  
* suspensão de acesso por inadimplência;  
* restauração de acesso após pagamento;  
* notificações relacionadas a matrículas e pagamentos;  
* processamento de reembolsos aplicáveis.

### **2.4.2 Regras de Negócio** {#2.4.2-regras-de-negócio}

**Cadastro e Login**

RN001 \- O sistema deve permitir o acesso às funcionalidades restritas somente a usuários autenticados.

RN002 \- O sistema não deve permitir que o usuário cadastre-se em um curso sem estar logado.

RN003 \- Todos os usuários devem conseguir recuperar a sua senha.

RN004 \- O sistema deve apresentar dois tipos de cadastro: professor ou aluno. O administrador só pode ser cadastrado internamente no sistema.

RN005 \- Senhas devem ser armazenadas com hash no banco de dados.

RN006 \- O sistema não deve permitir cadastro de dois usuários com o mesmo e-mail/CPF.

RN007 \- O sistema deve exigir que o e-mail informado no cadastro possua formato válido.

RN008 \- O CPF informado no cadastro deve possuir formato válido.

RN009 \- O sistema deve bloquear o acesso à conta após determinado número de tentativas consecutivas de login malsucedidas.

RN010 \- O usuário deve confirmar seu endereço de e-mail antes de utilizar determinadas funcionalidades do sistema.

**CRUD \- Usuários**

RN011 \- Alunos podem excluir seus dados caso não estejam matriculados em nenhum curso.

RN012 \- Professores não podem excluir suas contas caso possuam ao menos 1 curso disponível para matrícula.

RN013 \- Todos os usuários devem conseguir alterar os seus dados.

RN014 \- Apenas o Administrador pode alterar e excluir dados de outros usuários.

RN015 \- Alunos só podem visualizar dados não sensíveis dos professores.

RN016 \- Professores só devem visualizar os dados dos alunos que forem necessários para o curso.

RN017 \- Apenas o administrador deve ser capaz de visualizar todos os dados de todos os usuários.

RN018 \- O sistema não deve permitir que um usuário altere seu próprio tipo de cadastro (para aluno, professor ou administrador).

RN019 \- O administrador não pode excluir sua própria conta.

RN020 \- Ao excluir um usuário, o sistema deve preservar os registros necessários para manter a integridade das matrículas e pagamentos realizados.

**CRUD \- Cursos**

RN021 \- Um professor pode possuir múltiplos cursos.

RN022 \- Todo curso deve possuir nome, descrição, valor, horário, duração, número máximo de vagas e professor responsável.

RN023 \- O professor deve conseguir abrir ou fechar um curso para novas matrículas.

RN024 \- Cada curso deve possuir um número máximo de vagas definido pelo professor.

RN025 \- O professor não pode excluir um curso que possua alunos matriculados.

RN026 \- O horário de um curso não pode ser alterado para um período que conflite com outro curso disponível do mesmo professor.

RN027 \- O professor não pode disponibilizar um curso sem que todas as informações obrigatórias estejam preenchidas.

RN028 \- Um curso indisponível não pode receber novas matrículas.

RN029 \- O número máximo de vagas de um curso não pode ser inferior ao número de alunos já matriculados.

RN030 \- Um professor não pode disponibilizar um curso que esteja com todas as vagas ocupadas.

**Matrículas**

RN031 \- O sistema não deve permitir matrícula em curso que já atingiu o limite de vagas.

RN032 \- Alunos podem se matricular em um ou mais cursos.

RN033 \- O sistema não deve permitir que alunos matriculem-se em dois ou mais cursos no mesmo dia e horário.

RN034 \- Professores devem ser responsáveis por aceitar a matrícula de um aluno.

RN035 \- O aluno pode cancelar sua matrícula em um curso a qualquer momento.

RN036 \- Reembolsos só são possíveis em caso de cancelamento por parte do curso ou do professor.

RN037 \- Caso o professor torne um curso indisponível antes que ele seja finalizado, os alunos já matriculados devem ser notificados e reembolsados.

RN038 \- O sistema deve registrar a data e o horário em que cada matrícula foi realizada.

RN039 \- Um aluno não pode possuir mais de uma matrícula ativa para o mesmo curso.

RN040 \- Uma matrícula somente será considerada efetivada após a aprovação do professor e a confirmação do pagamento.

RN041 \- O professor deve ser notificado quando um novo aluno solicitar matrícula em seu curso.

RN042 \- O aluno deve ser notificado quando sua solicitação de matrícula for aceita ou recusada pelo professor.

RN043 \- Caso uma matrícula seja recusada pelo professor, o aluno não terá acesso ao curso.

RN044 \- O cancelamento de uma matrícula deve alterar seu status para cancelada, preservando seu histórico no sistema.

RN045 \- Quando um aluno cancelar sua matrícula, a vaga correspondente não deve ser disponibilizada automaticamente, cabendo ao professor decidir abrir a vaga ou não.

RN046 \- O professor deve poder disponibilizar novamente uma vaga após o cancelamento de uma matrícula.

**Pagamentos**

RN047 \- O aluno deve realizar o pagamento mensalmente para manter sua matrícula ativa.

RN048 \- O aluno somente poderá realizar o pagamento após a aprovação da solicitação de matrícula pelo professor.

RN049 \- O pagamento de cada curso deve ser realizado em cobranças mensais enquanto a matrícula permanecer ativa.

RN050 \- O sistema deve registrar o status de cada pagamento (pendente, aprovado, recusado, vencido ou estornado).

RN051 \- Em caso de pagamento recusado, o aluno deve ser notificado e ter a possibilidade de realizar uma nova tentativa de pagamento.

RN052 \- O sistema deve gerar automaticamente a cobrança mensal enquanto a matrícula estiver ativa.

RN053 \- O sistema deve conferir se o pagamento foi realizado antes de liberar o acesso ao curso.

RN054 \- O sistema não deve armazenar dados sensíveis referentes ao pagamento no banco de dados.

RN055 \- Caso o pagamento mensal não seja realizado até a data de vencimento, o acesso do aluno ao curso deve ser suspenso automaticamente.

RN056 \- O sistema deve notificar o aluno sobre pagamentos próximos do vencimento ou já vencidos.

RN057 \- O aluno não deve ser cobrado após o cancelamento de sua matrícula.

RN058 \- O acesso ao curso deve ser restaurado automaticamente após a confirmação de um pagamento em atraso.

RN059 \- Em caso de cancelamento do curso pelo professor, os pagamentos referentes ao período não utilizado devem ser reembolsados.

## **2.5 Modelagem do Banco de Dados** {#2.5-modelagem-do-banco-de-dados}

	O dicionário de dados foi montado conforme as necessidades do sistema que envolve CRUD de usuários e cursos, além de interações com uma API de pagamentos.
