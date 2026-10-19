package br.com.tentaculomimico.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;

@Document("webhooks_processados")
public record WebhookProcessado(@Id String id, @Field("evento_externo_id") String eventoExternoId,
                                @Field("processado_em") Instant processadoEm) {}
