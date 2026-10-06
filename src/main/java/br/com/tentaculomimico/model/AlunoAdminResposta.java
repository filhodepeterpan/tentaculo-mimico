package br.com.tentaculomimico.model;

import java.util.List;

public class AlunoAdminResposta {

    private String id;
    private String nome;
    private String email;
    private List<Curso> cursos;

    public AlunoAdminResposta(String id, String nome, String email, List<Curso> cursos) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.cursos = cursos;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<Curso> getCursos() {
        return cursos;
    }

    public void setCursos(List<Curso> cursos) {
        this.cursos = cursos;
    }

}
