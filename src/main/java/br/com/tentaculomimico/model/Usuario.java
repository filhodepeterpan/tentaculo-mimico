package br.com.tentaculomimico.model;

import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    private String nome;
    private String cpf;

    @Field("data_nascimento")
    private java.time.LocalDate dataNascimento;

    private String email;

    @Field("tipo_usuario")
    private TipoUsuario tipoUsuario;

    private Autenticacao autenticacao;

    @Field("tentativas_login_falhas")
    private int tentativasLoginFalhas = 0;

    @Field("bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Field("foto_perfil")
    private String fotoPerfil;

    @Field("token_recuperacao_senha")
    private String tokenRecuperacaoSenha;

    @Field("data_expiracao_token")
    private LocalDateTime dataExpiracaoToken;

    public Usuario() {
        this.autenticacao = new Autenticacao();
    }
}