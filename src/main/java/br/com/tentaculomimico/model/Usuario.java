package br.com.tentaculomimico.model;

import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;
    private String email;
    private TipoUsuario tipoUsuario;
    private Provedor provedor;

    private Autenticacao autenticacao;


    private int tentativasLoginFalhas = 0;
    private LocalDateTime bloqueadoAte;


    private String tokenRecuperacaoSenha;
    private LocalDateTime dataExpiracaoToken;

    public Usuario() {
        this.autenticacao = new Autenticacao();
    }
}