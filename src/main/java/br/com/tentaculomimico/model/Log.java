package br.com.tentaculomimico.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "logs")
public class Log {

    @Id
    private String id;

    private String tipoAcao;
    private String tipoDeUsuario;
    private String descricao;
    private String autor;
    private String autorId;
    private String alvo;
    private String alvoId;
    private String alvoNome;

    @Indexed
    private LocalDateTime dataHora;

    public Log() {
    }

    public Log(String tipoAcao, String tipoDeUsuario, String descricao,
               String autor, String autorId,
               String alvo, String alvoId, String alvoNome,
               LocalDateTime dataHora) {
        this.tipoAcao = tipoAcao;
        this.tipoDeUsuario = tipoDeUsuario;
        this.descricao = descricao;
        this.autor = autor;
        this.autorId = autorId;
        this.alvo = alvo;
        this.alvoId = alvoId;
        this.alvoNome = alvoNome;
        this.dataHora = dataHora;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTipoAcao() { return tipoAcao; }
    public void setTipoAcao(String tipoAcao) { this.tipoAcao = tipoAcao; }

    public String getTipoDeUsuario() { return tipoDeUsuario; }
    public void setTipoDeUsuario(String tipoDeUsuario) { this.tipoDeUsuario = tipoDeUsuario; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }

    public String getAutorId() { return autorId; }
    public void setAutorId(String autorId) { this.autorId = autorId; }

    public String getAlvo() { return alvo; }
    public void setAlvo(String alvo) { this.alvo = alvo; }

    public String getAlvoId() { return alvoId; }
    public void setAlvoId(String alvoId) { this.alvoId = alvoId; }

    public String getAlvoNome() { return alvoNome; }
    public void setAlvoNome(String alvoNome) { this.alvoNome = alvoNome; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }
}