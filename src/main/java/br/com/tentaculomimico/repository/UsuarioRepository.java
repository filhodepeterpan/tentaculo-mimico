package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByTokenRecuperacaoSenha(String tokenRecuperacaoSenha);
}