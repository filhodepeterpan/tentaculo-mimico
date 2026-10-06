package br.com.tentaculomimico.model;

import java.util.List;

public class ProfessorAdminResposta {

    private String id;
    private String nome;
    private String email;
    private List<Curso> cursos;

    public ProfessorAdminResposta(String id, String nome, String email, List<Curso> cursos) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.cursos = cursos;
    }
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public List<Curso> getCursos() { return cursos; }
}
