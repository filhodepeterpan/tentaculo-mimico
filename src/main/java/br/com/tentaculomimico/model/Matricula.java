package br.com.tentaculomimico.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;


@Document (collection = "matriculas")
public class Matricula {

    // para o mongoDB
    @Id
    private String id;

    //para matricula
    private String cursoId;
    private LocalDateTime horarioMatricula;
    private StatusMatricula statusMatricula;
    private String alunoId;
    private String nomeAluno;


    public Matricula() {}

    public Matricula(String cursoId, String alunoId, String nomeAluno) {
        this.cursoId = cursoId;
        this.alunoId = alunoId;
        this.nomeAluno = nomeAluno;
        this.horarioMatricula=LocalDateTime.now();
        this.statusMatricula = StatusMatricula.PENDENTE;
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

    public StatusMatricula getStatusMatricula() {
        return statusMatricula;
    }

    public void setStatusMatricula(StatusMatricula statusMatricula) {
        this.statusMatricula = statusMatricula;
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
}





