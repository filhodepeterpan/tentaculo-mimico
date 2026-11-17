package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.Log;
import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


import java.util.List;

public interface LogRepository extends MongoRepository<Log, String>{

    Page<Log> findAllByOrderByDataHoraDesc(Pageable pageable);

}
