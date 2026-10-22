package br.com.tentaculomimico.model;

import java.util.List;

public class CursoAdminResposta {
    private Curso curso;
    private List<Matricula> matriculas;

    public CursoAdminResposta(Curso curso, List<Matricula> matriculas) {
        this.curso = curso;
        this.matriculas = matriculas;
    }

    public Curso getCurso() {
        return curso;
    }

    public List<Matricula> getMatriculas() {
        return matriculas;
    }
}
