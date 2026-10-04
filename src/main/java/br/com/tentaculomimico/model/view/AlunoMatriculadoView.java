package br.com.tentaculomimico.model.view;

// Um aluno + os cursos DAQUELE professor em que ele está matriculado,
// já formatados em texto (cursosTexto), pra exibir na aba "Alunos
// matriculados" do perfil do professor (fragments/aluno-item.html).
public class AlunoMatriculadoView {

    private String alunoId;
    private String alunoNome;
    private String alunoFotoPerfil;
    private String cursosTexto;

    public AlunoMatriculadoView() {}

    public AlunoMatriculadoView(String alunoId, String alunoNome, String alunoFotoPerfil, String cursosTexto) {
        this.alunoId = alunoId;
        this.alunoNome = alunoNome;
        this.alunoFotoPerfil = alunoFotoPerfil;
        this.cursosTexto = cursosTexto;
    }

    public String getAlunoId() { return alunoId; }
    public void setAlunoId(String alunoId) { this.alunoId = alunoId; }

    public String getAlunoNome() { return alunoNome; }
    public void setAlunoNome(String alunoNome) { this.alunoNome = alunoNome; }

    public String getAlunoFotoPerfil() { return alunoFotoPerfil; }
    public void setAlunoFotoPerfil(String alunoFotoPerfil) { this.alunoFotoPerfil = alunoFotoPerfil; }

    public String getCursosTexto() { return cursosTexto; }
    public void setCursosTexto(String cursosTexto) { this.cursosTexto = cursosTexto; }
}