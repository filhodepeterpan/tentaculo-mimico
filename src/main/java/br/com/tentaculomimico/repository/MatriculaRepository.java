package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Matricula;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatriculaRepository extends MongoRepository<Matricula, String> {

    List<Matricula> findByAlunoIdAndCursoId(String alunoId, String cursoId);
    List<Matricula> findByAlunoId(String alunoId);
    List<Matricula> findByCursoId(String cursoId);
    List<Matricula> findByCursoIdIn(java.util.Collection<String> cursoIds);

}