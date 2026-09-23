package br.com.tentaculomimico.model;

import java.time.LocalTime;

public class Horario {
    private DiaSemana diaSemana;
    private LocalTime inicio;
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


