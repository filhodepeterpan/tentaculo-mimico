package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.DiaSemana;
import br.com.tentaculomimico.model.Horario;
import br.com.tentaculomimico.repository.CursoRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import br.com.tentaculomimico.model.DiaSemana;
import br.com.tentaculomimico.model.StatusCurso;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final ProfessorLogadoProvider professorLogadoProvider;

    public CursoService(CursoRepository cursoRepository, ProfessorLogadoProvider professorLogadoProvider) {
        this.cursoRepository = cursoRepository;
        this.professorLogadoProvider = professorLogadoProvider;
    }

    // =========================================================================
    // ETAPA 1: Compara dois horários específicos para ver se batem no relógio
    // =========================================================================

    private boolean horariosSeSobrepoem(Horario horarioA, Horario horarioB) {

        // Se os dias da semana forem diferentes, não há sobreposição possível
        if (horarioA.getDiaSemana() != horarioB.getDiaSemana()) {
            return false;
        }
        // Verifica a intersecção de intervalos:
        // (InícioA < FimB) E (InícioB < FimA)
        if (horarioA.getInicio().isBefore(horarioB.getFim()) && horarioB.getInicio().isBefore(horarioA.getFim())) {
            return true;
        }
        return false;
    }


    // =========================================================================
    // ETAPA 2: Cruza todas as listas de horários de dois cursos diferentes
    // =========================================================================

    private boolean cursosConflitam(Curso cursoA, Curso cursoB) {

        // Compara cada bloco de horário do curso A com cada bloco de horário do curso B
        for (Horario horarioA : cursoA.getHorarios()) {
            for (Horario horarioB : cursoB.getHorarios()) {

                if (horariosSeSobrepoem(horarioA, horarioB)) {
                    return true;
                }
            }
        }
        return false;
    }


    // =========================================================================
    // ETAPA 3: Valida se o professor já tem aula marcada que bate com o novo curso
    // =========================================================================

    private boolean existeConflitoParaProfessor(Curso cursoNovo) {

        // Busca no banco os cursos que o professor já ministra
        List<Curso> cursosExistentes = cursoRepository.findByProfessorId(cursoNovo.getProfessorId());

        // Valida o novo curso contra cada um dos cursos já ministrados pelo professor
        for (Curso cursoExistente : cursosExistentes) {

            if (cursosConflitam(cursoNovo, cursoExistente)) {
                return true;
            }

        }

        return false;
    }




    public Curso criarCurso(String nome, String descricao, String cargaHoraria,
                            String preco, String vagasTotais, List<String> diasSemana,
                            String horaInicio, String horaFim,
                            String dataInicio, String dataFim) {

        Curso curso = new Curso();
        curso.setNome(nome);
        curso.setDescricao(descricao);
        curso.setCargaHoraria(Integer.parseInt(cargaHoraria));
        curso.setPreco(new BigDecimal(preco));
        int totalVagas = Integer.parseInt(vagasTotais);
        curso.setVagasTotais(totalVagas);
        curso.setVagasDisponiveis(totalVagas);

        LocalTime inicio = LocalTime.parse(horaInicio);
        LocalTime fim = LocalTime.parse(horaFim);

        List<Horario> horarios = new ArrayList<>();
        for (String dia : diasSemana) {
            DiaSemana diaSemana = DiaSemana.valueOf(dia);
            horarios.add(new Horario(diaSemana, inicio, fim));
        }
        curso.setHorarios(horarios);

        curso.setDataInicio(LocalDate.parse(dataInicio));
        if (dataFim != null && !dataFim.isBlank()) {
            curso.setDataFim(LocalDate.parse(dataFim));
        }
        curso.setDataCadastro(LocalDate.now());
        curso.setStatus(StatusCurso.DISPONIVEL);
        curso.setAtivo(true);
        curso.setProfessorId(professorLogadoProvider.obterProfessorIdAtual());
        curso.setProfessorNome(professorLogadoProvider.obterProfessorNomeAtual());

        return curso;
    }



    public Curso cadastrarCurso(String nome, String descricao, String cargaHoraria,
                                String preco, String vagasTotais, List<String> diasSemana,
                                String horaInicio, String horaFim,
                                String dataInicio, String dataFim) {

        Curso curso = criarCurso(nome, descricao, cargaHoraria, preco, vagasTotais,
                diasSemana, horaInicio, horaFim, dataInicio, dataFim);

        //se tiver conflito ele interrompe a execussão
        if (existeConflitoParaProfessor(curso)) {
            throw new RuntimeException("Já existe um curso seu nesse horário.");
        }

        return cursoRepository.save(curso);
    }


    }