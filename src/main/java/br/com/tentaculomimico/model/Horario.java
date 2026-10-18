package br.com.tentaculomimico.model;

import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalTime;

// Os nomes no banco (documento embutido em "cursos.horarios") são snake_case:
// dia_semana, hora_inicio e hora_fim. dia_semana é gravado como número (1..7,
// igual a DiaSemana.getNumero()) e as horas como texto "HH:mm" — por isso há
// converters registrados em MongoConfig para os tipos DiaSemana e LocalTime.
public class Horario {

    @Field("dia_semana")
    private DiaSemana diaSemana;

    @Field("hora_inicio")
    private LocalTime inicio;

    @Field("hora_fim")
    private LocalTime fim;

    public Horario(DiaSemana diaSemana, LocalTime inicio, LocalTime fim) {
        this.inicio = inicio;
        this.fim = fim;
        this.diaSemana = diaSemana;
    }

    public DiaSemana getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFim() {
        return fim;
    }
}


