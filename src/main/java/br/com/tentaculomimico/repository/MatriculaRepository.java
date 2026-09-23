package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Matricula;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatriculaRepository extends MongoRepository<Matricula, String> {

    List<Matricula> findByAlunoIdAndCursoId(String alunoId, String cursoId);

}