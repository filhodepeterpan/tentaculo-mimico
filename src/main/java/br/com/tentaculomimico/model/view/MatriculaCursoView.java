package br.com.tentaculomimico.model.view;

// Combina dados de Matricula + Curso pra exibição no card do perfil do
// aluno (fragments/card-matricula.html).
public class MatriculaCursoView {

    private String id;
    private String cursoId;
    private String cursoNome;
    private int cursoCargaHoraria;
    private String cursoImagemCapa;
    private String status;

    public MatriculaCursoView() {}

    public MatriculaCursoView(String id, String cursoId, String cursoNome, int cursoCargaHoraria,
                              String cursoImagemCapa, String status) {
        this.id = id;
        this.cursoId = cursoId;
        this.cursoNome = cursoNome;
        this.cursoCargaHoraria = cursoCargaHoraria;
        this.cursoImagemCapa = cursoImagemCapa;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCursoId() { return cursoId; }
    public void setCursoId(String cursoId) { this.cursoId = cursoId; }

    public String getCursoNome() { return cursoNome; }
    public void setCursoNome(String cursoNome) { this.cursoNome = cursoNome; }

    public int getCursoCargaHoraria() { return cursoCargaHoraria; }
    public void setCursoCargaHoraria(int cursoCargaHoraria) { this.cursoCargaHoraria = cursoCargaHoraria; }

    public String getCursoImagemCapa() { return cursoImagemCapa; }
    public void setCursoImagemCapa(String cursoImagemCapa) { this.cursoImagemCapa = cursoImagemCapa; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}