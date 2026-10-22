package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Curso;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CursoRepository extends MongoRepository<Curso,String> {

    List<Curso> findByProfessorId(String professorId);

    //painel admin: conta a quantidade de cursos
    long countByAtivoTrue();
}
