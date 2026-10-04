package br.com.tentaculomimico.model;

import br.com.tentaculomimico.model.enums.Provedor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
public class Autenticacao {
    @Field("senha_hash")
    private String senhaHash;

    @Field("provedor")
    private Provedor provedor = Provedor.LOCAL;

    @Field("provedor_id")
    private String provedorId;
}