package br.com.tentaculomimico.dto;


public class ResumoAdminResposta
{
    private final long alunos;
    private final long professores;
    private final long cursosAtivos;
    private final long matriculas;

    public ResumoAdminResposta(long alunos, long professores, long cursosAtivos, long matriculas) {
        this.alunos = alunos;
        this.professores = professores;
        this.cursosAtivos = cursosAtivos;
        this.matriculas = matriculas;
    }

    public long getAlunos() {
        return alunos;
    }

    public long getProfessores() {
        return professores;
    }

    public long getCursosAtivos() {
        return cursosAtivos;
    }

    public long getMatriculas() {
        return matriculas;
    }
}
