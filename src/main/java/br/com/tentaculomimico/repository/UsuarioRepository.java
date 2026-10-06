package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    // Usado na edição de perfil pra impedir CPF repetido (o CPF é guardado só com dígitos).
    Optional<Usuario> findByCpf(String cpf);

    Optional<Usuario> findByTokenRecuperacaoSenha(String tokenRecuperacaoSenha);

    //painel admin: conta a quantidade de usuários
    long countByTipoUsuario(TipoUsuario tipoUsuario);
    List<Usuario> findByTipoUsuario(TipoUsuario tipoUsuario);


}
