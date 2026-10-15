package br.com.tentaculomimico.model.view;

import java.util.List;

// Um aluno + os cursos DAQUELE professor em que ele está matriculado,
// já formatados em texto (cursosTexto), pra exibir na aba "Alunos
// matriculados" do perfil do professor (fragments/aluno-item.html).
// "matriculas" traz uma entrada por curso (id da matrícula + nome do curso):
// é o que o modal de cancelamento usa pra o professor escolher qual cancelar.
public class AlunoMatriculadoView {

    public record MatriculaItem(String matriculaId, String cursoNome) {
        // Thymeleaf/SpEL lê records por getter; estes dois deixam o template simples.
        public String getMatriculaId() { return matriculaId; }
        public String getCursoNome() { return cursoNome; }
    }

    private String alunoId;
    private String alunoNome;
    private String alunoFotoPerfil;
    private String cursosTexto;
    private List<MatriculaItem> matriculas = List.of();

    public AlunoMatriculadoView() {}

    public AlunoMatriculadoView(String alunoId, String alunoNome, String alunoFotoPerfil, String cursosTexto) {
        this.alunoId = alunoId;
        this.alunoNome = alunoNome;
        this.alunoFotoPerfil = alunoFotoPerfil;
        this.cursosTexto = cursosTexto;
    }

    public AlunoMatriculadoView(String alunoId, String alunoNome, String alunoFotoPerfil, String cursosTexto,
                                List<MatriculaItem> matriculas) {
        this(alunoId, alunoNome, alunoFotoPerfil, cursosTexto);
        this.matriculas = matriculas != null ? matriculas : List.of();
    }

    public String getAlunoId() { return alunoId; }
    public void setAlunoId(String alunoId) { this.alunoId = alunoId; }

    public String getAlunoNome() { return alunoNome; }
    public void setAlunoNome(String alunoNome) { this.alunoNome = alunoNome; }

    public String getAlunoFotoPerfil() { return alunoFotoPerfil; }
    public void setAlunoFotoPerfil(String alunoFotoPerfil) { this.alunoFotoPerfil = alunoFotoPerfil; }

    public String getCursosTexto() { return cursosTexto; }
    public void setCursosTexto(String cursosTexto) { this.cursosTexto = cursosTexto; }

    public List<MatriculaItem> getMatriculas() { return matriculas; }
    public void setMatriculas(List<MatriculaItem> matriculas) { this.matriculas = matriculas; }
}
