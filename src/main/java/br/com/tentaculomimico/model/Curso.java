package br.com.tentaculomimico.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Document(collection = "cursos")
public class Curso {

    @Id
    private String id;
    private String professorId;
    private String professorNome;
    private String nome;
    private String descricao;
    private int cargaHoraria;
    private List<Horario> horarios;
    private BigDecimal preco;
    private int vagasTotais;
    private int vagasDisponiveis;
    private String professorNome;
    private String quando;
    private String imagemCapa;

    public Curso(String id, String nome, int cargaHoraria, String descricao,
                 double preco, int vagasTotais, int vagasDisponiveis,
                 String professorNome, String quando, String imagemCapa) {
        this.id = id;
    }

    public String getProfessorId() {
        return professorId;
    }

    public void setProfessorId(String professorId) {
        this.professorId = professorId;
    }

    public String getProfessorNome() {
        return professorNome;
    }

    public void setProfessorNome(String professorNome) {
        this.professorNome = professorNome;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getCargaHoraria() {
        return cargaHoraria;
    }

    public void setCargaHoraria(int cargaHoraria) {
        this.cargaHoraria = cargaHoraria;
    }

    public List<Horario> getHorarios() {
        return horarios;
    }

    public void setHorarios(List<Horario> horarios) {
        this.horarios = horarios;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public int getVagasTotais() {
        return vagasTotais;
    }

    public void setVagasTotais(int vagasTotais) {
        this.vagasTotais = vagasTotais;
    }

    public int getVagasDisponiveis() {
        return vagasDisponiveis;
    }

    public void setVagasDisponiveis(int vagasDisponiveis) {
        this.vagasDisponiveis = vagasDisponiveis;
        this.professorNome = professorNome;
        this.quando = quando;
        this.imagemCapa = imagemCapa;
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
    public String getImagemCapa() { return imagemCapa; }
}
