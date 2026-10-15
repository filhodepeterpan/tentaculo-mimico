package br.com.tentaculomimico.model;

import java.util.List;

public class ProfessorAdminResposta {

    private String id;
    private String nome;
    private String email;
    private List<Curso> cursos;
    private String fotoPerfil;

    public ProfessorAdminResposta(String id, String nome, String email, List<Curso> cursos, String fotoPerfil) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.cursos = cursos;
        this.fotoPerfil = fotoPerfil;
    }


    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public List<Curso> getCursos() { return cursos; }
    public String getFotoPerfil() {return fotoPerfil;}
}
