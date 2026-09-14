package br.com.tentaculomimico.model;

public class Curso {

    private String id;
    private String nome;
    private int cargaHoraria;
    private String descricao;
    private double preco;
    private int vagasTotais;
    private int vagasDisponiveis;
    private String professorNome;
    private String quando;

    public Curso(String id, String nome, int cargaHoraria, String descricao,
                 double preco, int vagasTotais, int vagasDisponiveis,
                 String professorNome, String quando) {
        this.id = id;
        this.nome = nome;
        this.cargaHoraria = cargaHoraria;
        this.descricao = descricao;
        this.preco = preco;
        this.vagasTotais = vagasTotais;
        this.vagasDisponiveis = vagasDisponiveis;
        this.professorNome = professorNome;
        this.quando = quando;
    }

    public String getId() { return id; }
    public String getNome() { return nome; }
    public int getCargaHoraria() { return cargaHoraria; }
    public String getDescricao() { return descricao; }
    public double getPreco() { return preco; }
    public int getVagasTotais() { return vagasTotais; }
    public int getVagasDisponiveis() { return vagasDisponiveis; }
    public String getProfessorNome() { return professorNome; }
    public String getQuando() { return quando; }
}
