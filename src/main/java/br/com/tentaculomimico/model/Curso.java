package br.com.tentaculomimico.model;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Os nomes dos campos no banco são snake_case (ver docs/data_dictionary.md e o
// $jsonSchema da coleção "cursos"), então cada campo divergente precisa de @Field.
// Sem isso, o Spring Data procura a chave com o nome Java (camelCase), não acha e
// silenciosamente usa o default (int vira 0, objeto vira null) — era o que fazia
// "vagasDisponiveis" chegar como 0 e o curso aparecer sempre lotado.
@Document(collection = "cursos")
public class Curso {

    @Id
    private String id;

    @Field(value = "professor_id", targetType = FieldType.OBJECT_ID)
    private String professorId;

    @Field("professor_nome")
    private String professorNome;

    @Field("professor_foto_perfil")
    private String professorFotoPerfil;

    // Estes já batem com o banco: mantidos sem @Field.
    private String nome;
    private String descricao;

    @Field("carga_horaria")
    private int cargaHoraria;

    private List<Horario> horarios;
    private BigDecimal preco;

    @Field("vagas_totais")
    private int vagasTotais;

    @Field("vagas_disponiveis")
    private int vagasDisponiveis;

    private StatusCurso status;

    @Field("data_inicio")
    private LocalDate dataInicio;

    @Field("data_fim")
    private LocalDate dataFim;

    @Field("data_cadastro")
    private LocalDate dataCadastro;

    private Boolean ativo;

    @Field("imagem_capa")
    private String imagemCapa;

    //private String quando;


    public Curso(){}

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getProfessorFotoPerfil() {
        return professorFotoPerfil;
    }

    public void setProfessorFotoPerfil(String professorFotoPerfil) {
        this.professorFotoPerfil = professorFotoPerfil;
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
    }

    public StatusCurso getStatus() {
        return status;
    }

    public void setStatus(StatusCurso status) {
        this.status = status;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public LocalDate getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDate dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public Boolean getAtivo() {
        return ativo;
    }

    public void setAtivo(Boolean ativo) {
        this.ativo = ativo;
    }

    public String getImagemCapa() {
        return imagemCapa;
    }

    public void setImagemCapa(String imagemCapa) {
        this.imagemCapa = imagemCapa;
    }
}
