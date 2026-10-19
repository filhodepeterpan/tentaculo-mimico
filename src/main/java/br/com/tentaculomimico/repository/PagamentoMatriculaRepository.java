package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.PagamentoMatricula;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.Optional;

public interface PagamentoMatriculaRepository extends MongoRepository<PagamentoMatricula, String> {
    Optional<PagamentoMatricula> findByMatriculaId(String id);
    @Query("{'tentativas.reference_id': ?0}")
    Optional<PagamentoMatricula> porReferencia(String referencia);
}
