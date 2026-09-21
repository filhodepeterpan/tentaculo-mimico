package br.com.tentaculomimico.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cursos")
public class Curso {

    @Id
    private String id;

    private String nome;
    private int cargaHoraria;
    private String descricao;
    private double preco;
    private int vagasTotais;
    private int vagasDisponiveis;
    private String professorNome;
    private String horario;
    private String imagemCapa;
}