package br.com.tentaculomimico.model.view;

import java.util.List;

// O que o calendário da semana recebe do servidor (JSON de GET /api/calendario/semana).
// Os dias vão de domingo (indice 0) a sábado (indice 6). Datas em ISO (yyyy-MM-dd)
// e horas em "HH:mm", para o JavaScript não depender de fuso nem de formato.
public record SemanaCalendarioView(List<Dia> dias, List<Evento> eventos) {

    public record Dia(int indice, String nome, String data, int diaMes, boolean hoje) {
    }

    // Uma aula: um curso num dia da semana, de "inicio" a "fim".
    public record Evento(String cursoId, String nome, String imagemCapa,
                         int dia, String inicio, String fim) {
    }
}
