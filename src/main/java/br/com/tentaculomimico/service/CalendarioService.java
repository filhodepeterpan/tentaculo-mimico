package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.DiaSemana;
import br.com.tentaculomimico.model.Horario;
import br.com.tentaculomimico.model.view.SemanaCalendarioView;
import br.com.tentaculomimico.repository.CursoRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Monta a semana atual ou a próxima (domingo a sábado) com as aulas de cada curso.
 * Um curso só aparece num dia se a data daquele dia estiver dentro do período
 * dele (data de início até data de término; sem término = sem fim).
 */
@Service
public class CalendarioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_SEMANAS_A_FRENTE = 1; // semana atual e a próxima
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] NOMES_DIAS = {
            "Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"
    };

    private final CursoRepository cursoRepository;

    public CalendarioService(CursoRepository cursoRepository) {
        this.cursoRepository = cursoRepository;
    }

    public SemanaCalendarioView semanaAtual() {
        return semana(0);
    }

    /**
     * Monta uma semana (domingo a sábado). 0 = semana atual, 1 = próxima semana; valores
     * fora desse intervalo são ajustados (não dá para ver semanas passadas nem mais de uma
     * semana à frente).
     */
    public SemanaCalendarioView semana(int semanasAFrente) {
        int deslocamento = Math.max(0, Math.min(semanasAFrente, MAX_SEMANAS_A_FRENTE));
        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate domingo = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
                .plusWeeks(deslocamento);

        List<SemanaCalendarioView.Dia> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate data = domingo.plusDays(i);
            dias.add(new SemanaCalendarioView.Dia(
                    i, NOMES_DIAS[i], data.toString(), data.getDayOfMonth(), data.equals(hoje)));
        }

        List<SemanaCalendarioView.Evento> eventos = new ArrayList<>();
        for (Curso curso : cursoRepository.findAll()) {
            if (Boolean.FALSE.equals(curso.getAtivo()) || curso.getHorarios() == null) {
                continue;
            }
            for (Horario horario : curso.getHorarios()) {
                int indice = indiceDoDia(horario.getDiaSemana());
                if (indice < 0 || horario.getInicio() == null || horario.getFim() == null) {
                    continue;
                }
                if (!cursoAconteceEm(curso, domingo.plusDays(indice))) {
                    continue;
                }
                eventos.add(new SemanaCalendarioView.Evento(
                        curso.getId(),
                        curso.getNome(),
                        curso.getImagemCapa(),
                        indice,
                        horario.getInicio().format(HHMM),
                        horario.getFim().format(HHMM)));
            }
        }
        eventos.sort(Comparator.comparingInt(SemanaCalendarioView.Evento::dia)
                .thenComparing(SemanaCalendarioView.Evento::inicio));

        return new SemanaCalendarioView(dias, eventos);
    }

    // A data cai dentro do período do curso? (término vazio = sem data de término)
    private boolean cursoAconteceEm(Curso curso, LocalDate data) {
        boolean jaComecou = curso.getDataInicio() == null || !data.isBefore(curso.getDataInicio());
        boolean naoTerminou = curso.getDataFim() == null || !data.isAfter(curso.getDataFim());
        return jaComecou && naoTerminou;
    }

    // Domingo = 0 ... sábado = 6. Usa o nome do enum (os mesmos valores do formulário)
    // para não depender de como DiaSemana numera os dias.
    private int indiceDoDia(DiaSemana dia) {
        if (dia == null) {
            return -1;
        }
        return switch (dia.name()) {
            case "DOMINGO" -> 0;
            case "SEGUNDA" -> 1;
            case "TERCA" -> 2;
            case "QUARTA" -> 3;
            case "QUINTA" -> 4;
            case "SEXTA" -> 5;
            case "SABADO" -> 6;
            default -> -1;
        };
    }
}
