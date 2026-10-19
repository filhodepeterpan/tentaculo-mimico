package br.com.tentaculomimico.repository;

import br.com.tentaculomimico.model.WebhookProcessado;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WebhookProcessadoRepository extends MongoRepository<WebhookProcessado, String> {}
