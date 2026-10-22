package br.com.tentaculomimico.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;

/**
 * Os nomes dos campos seguem o $jsonSchema da collection "matriculas"
 * (aluno_id, curso_id e status são obrigatórios e o schema valida os tipos).
 */
@Document(collection = "matriculas")
public class Matricula {

    // para o mongoDB
    @Id
    private String id;

    // O schema exige objectId; sem targetType o Spring gravaria uma String.
    @Field(name = "curso_id", targetType = FieldType.OBJECT_ID)
    private String cursoId;

    @Field(name = "aluno_id", targetType = FieldType.OBJECT_ID)
    private String alunoId;

    @Field("nome_aluno")
    private String nomeAluno;

    @Field("data_solicitacao")
    private LocalDateTime horarioMatricula;

    // Guardado como o texto que o schema aceita ("pendente", "aceita_aguardando_pagamento", ...).
    // O resto do código continua usando o enum por getStatusMatricula()/setStatusMatricula().
    @Field("status")
    private String status;

    // false até a matrícula ficar ATIVA (aprovada + paga); volta a false se for cancelada.
    @Field("acesso_liberado")
    private boolean acessoLiberado = false;

    @Field("data_resposta_professor")
    private LocalDateTime dataRespostaProfessor;

    @Field("data_cancelamento")
    private LocalDateTime dataCancelamento;


    public Matricula() {}

    public Matricula(String cursoId, String alunoId, String nomeAluno) {
        this.cursoId = cursoId;
        this.alunoId = alunoId;
        this.nomeAluno = nomeAluno;
        this.horarioMatricula = LocalDateTime.now();
        setStatusMatricula(StatusMatricula.PENDENTE);
        this.acessoLiberado = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCursoId() {
        return cursoId;
    }

    public void setCursoId(String cursoId) {
        this.cursoId = cursoId;
    }

    public LocalDateTime getHorarioMatricula() {
        return horarioMatricula;
    }

    public void setHorarioMatricula(LocalDateTime horarioMatricula) {
        this.horarioMatricula = horarioMatricula;
    }

    // @Transient: este getter não é uma propriedade do documento; quem persiste é o campo "status".
    @Transient
    public StatusMatricula getStatusMatricula() {
        return paraEnum(status);
    }

    public void setStatusMatricula(StatusMatricula statusMatricula) {
        this.status = paraTexto(statusMatricula);
    }

    public String getAlunoId() {
        return alunoId;
    }

    public void setAlunoId(String alunoId) {
        this.alunoId = alunoId;
    }

    public String getNomeAluno() {
        return nomeAluno;
    }

    public void setNomeAluno(String nomeAluno) {
        this.nomeAluno = nomeAluno;
    }

    public boolean isAcessoLiberado() {
        return acessoLiberado;
    }

    public void setAcessoLiberado(boolean acessoLiberado) {
        this.acessoLiberado = acessoLiberado;
    }

    public LocalDateTime getDataRespostaProfessor() {
        return dataRespostaProfessor;
    }

    public void setDataRespostaProfessor(LocalDateTime dataRespostaProfessor) {
        this.dataRespostaProfessor = dataRespostaProfessor;
    }

    public LocalDateTime getDataCancelamento() {
        return dataCancelamento;
    }

    public void setDataCancelamento(LocalDateTime dataCancelamento) {
        this.dataCancelamento = dataCancelamento;
    }

    // ---- conversão enum <-> texto do schema ----

    private static String paraTexto(StatusMatricula s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case PENDENTE -> "pendente";
            case AGUARDANDO_PAGAMENTO -> "aceita_aguardando_pagamento";
            case ATIVA -> "ativa";
            case RECUSADA -> "recusada";
            case CANCELADA -> "cancelada";
            default -> throw new IllegalStateException("Status sem correspondente no schema: " + s);
        };
    }

    private static StatusMatricula paraEnum(String texto) {
        if (texto == null) {
            return null;
        }
        return switch (texto) {
            case "pendente" -> StatusMatricula.PENDENTE;
            case "aceita_aguardando_pagamento" -> StatusMatricula.AGUARDANDO_PAGAMENTO;
            case "ativa" -> StatusMatricula.ATIVA;
            case "recusada" -> StatusMatricula.RECUSADA;
            case "cancelada" -> StatusMatricula.CANCELADA;
            default -> throw new IllegalStateException("Status desconhecido no banco: " + texto);
        };
    }
}
