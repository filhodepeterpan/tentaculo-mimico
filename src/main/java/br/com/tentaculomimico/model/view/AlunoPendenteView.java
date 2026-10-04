package br.com.tentaculomimico.model.view;

// Um aluno com uma solicitação de matrícula em processo (pendente de
// aprovação, ou aprovada aguardando pagamento) num curso DAQUELE professor.
// Usado na aba "Matrículas pendentes" (fragments/aluno-item.html) e no
// modal de responder (fragments/modal-responder-matricula.html).
public class AlunoPendenteView {

    private String alunoId;
    private String alunoNome;
    private String alunoFotoPerfil;
    private String alunoEmail;
    private String matriculaId;
    private String statusTexto;
    private String cursoNome;

    public AlunoPendenteView() {}

    public AlunoPendenteView(String alunoId, String alunoNome, String alunoFotoPerfil, String alunoEmail,
                             String matriculaId, String statusTexto, String cursoNome) {
        this.alunoId = alunoId;
        this.alunoNome = alunoNome;
        this.alunoFotoPerfil = alunoFotoPerfil;
        this.alunoEmail = alunoEmail;
        this.matriculaId = matriculaId;
        this.statusTexto = statusTexto;
        this.cursoNome = cursoNome;
    }

    public String getAlunoId() { return alunoId; }
    public void setAlunoId(String alunoId) { this.alunoId = alunoId; }

    public String getAlunoNome() { return alunoNome; }
    public void setAlunoNome(String alunoNome) { this.alunoNome = alunoNome; }

    public String getAlunoFotoPerfil() { return alunoFotoPerfil; }
    public void setAlunoFotoPerfil(String alunoFotoPerfil) { this.alunoFotoPerfil = alunoFotoPerfil; }

    public String getAlunoEmail() { return alunoEmail; }
    public void setAlunoEmail(String alunoEmail) { this.alunoEmail = alunoEmail; }

    public String getMatriculaId() { return matriculaId; }
    public void setMatriculaId(String matriculaId) { this.matriculaId = matriculaId; }

    public String getStatusTexto() { return statusTexto; }
    public void setStatusTexto(String statusTexto) { this.statusTexto = statusTexto; }

    public String getCursoNome() { return cursoNome; }
    public void setCursoNome(String cursoNome) { this.cursoNome = cursoNome; }
}