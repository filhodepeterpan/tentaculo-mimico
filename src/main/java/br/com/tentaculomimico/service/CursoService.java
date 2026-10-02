package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.DiaSemana;
import br.com.tentaculomimico.model.Horario;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusCurso;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.MatriculaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class CursoService {

    private final CursoRepository cursoRepository;
    private final MatriculaRepository matriculaRepository;
    private final ProfessorLogadoProvider professorLogadoProvider;
    private final SessaoService sessaoService;

    public CursoService(CursoRepository cursoRepository,
                        MatriculaRepository matriculaRepository,
                        ProfessorLogadoProvider professorLogadoProvider,
                        SessaoService sessaoService) {
        this.cursoRepository = cursoRepository;
        this.matriculaRepository = matriculaRepository;
        this.professorLogadoProvider = professorLogadoProvider;
        this.sessaoService = sessaoService;
    }

    // =========================================================================
    // Conflito de horário do professor
    // =========================================================================

    // ETAPA 1: dois blocos de horário batem no relógio?
    private boolean horariosSeSobrepoem(Horario a, Horario b) {
        if (a.getDiaSemana() != b.getDiaSemana()) {
            return false;
        }
        // (InícioA < FimB) E (InícioB < FimA)
        return a.getInicio().isBefore(b.getFim()) && b.getInicio().isBefore(a.getFim());
    }

    // ETAPA 1.5: os períodos (data início/fim) dos cursos se cruzam?
    // Sem isso, dois cursos no mesmo dia/hora mas em semestres diferentes
    // seriam tratados como conflito. dataFim vazia = sem data de término.
    private boolean periodosSeSobrepoem(Curso a, Curso b) {
        if (a.getDataInicio() == null || b.getDataInicio() == null) {
            return true; // sem dados suficientes: assume o caso mais seguro
        }
        LocalDate fimA = a.getDataFim() != null ? a.getDataFim() : LocalDate.MAX;
        LocalDate fimB = b.getDataFim() != null ? b.getDataFim() : LocalDate.MAX;
        return !a.getDataInicio().isAfter(fimB) && !b.getDataInicio().isAfter(fimA);
    }

    // ETAPA 2: cruza todos os blocos de horário de dois cursos
    private boolean cursosConflitam(Curso a, Curso b) {
        if (!periodosSeSobrepoem(a, b)) {
            return false;
        }
        for (Horario ha : a.getHorarios()) {
            for (Horario hb : b.getHorarios()) {
                if (horariosSeSobrepoem(ha, hb)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ETAPA 3: o professor já tem aula nesse horário?
    private boolean existeConflitoParaProfessor(Curso cursoNovo) {
        List<Curso> existentes = cursoRepository.findByProfessorId(cursoNovo.getProfessorId());
        for (Curso existente : existentes) {
            if (cursosConflitam(cursoNovo, existente)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Cadastro
    // =========================================================================

    public Curso criarCurso(String nome, String descricao, String cargaHoraria,
                            String preco, String vagasTotais, List<String> diasSemana,
                            String horaInicio, String horaFim,
                            String dataInicio, String dataFim, String urlImagem) {

        if (nome == null || nome.isBlank()) {
            throw new RuntimeException("Informe o nome do curso.");
        }
        if (diasSemana == null || diasSemana.isEmpty()) {
            throw new RuntimeException("Selecione ao menos um dia da semana.");
        }

        Curso curso = new Curso();
        try {
            curso.setNome(nome.trim());
            curso.setDescricao(descricao);

            int carga = Integer.parseInt(cargaHoraria.trim());
            BigDecimal valor = new BigDecimal(preco.trim());
            int totalVagas = Integer.parseInt(vagasTotais.trim());
            if (carga < 1) {
                throw new RuntimeException("A carga horária deve ser maior que zero.");
            }
            if (valor.signum() < 0) {
                throw new RuntimeException("O preço não pode ser negativo.");
            }
            if (totalVagas < 1) {
                throw new RuntimeException("O curso precisa ter ao menos uma vaga.");
            }
            curso.setCargaHoraria(carga);
            curso.setPreco(valor);
            curso.setVagasTotais(totalVagas);
            curso.setVagasDisponiveis(totalVagas);
            curso.setImagemCapa(urlImagem);

            LocalTime inicio = LocalTime.parse(horaInicio);
            LocalTime fim = LocalTime.parse(horaFim);
            if (!fim.isAfter(inicio)) {
                throw new RuntimeException("O horário de término deve ser depois do início.");
            }

            List<Horario> horarios = new ArrayList<>();
            for (String dia : diasSemana) {
                horarios.add(new Horario(DiaSemana.valueOf(dia), inicio, fim));
            }
            curso.setHorarios(horarios);

            LocalDate dtInicio = LocalDate.parse(dataInicio);
            curso.setDataInicio(dtInicio);
            if (dataFim != null && !dataFim.isBlank()) {
                LocalDate dtFim = LocalDate.parse(dataFim);
                if (dtFim.isBefore(dtInicio)) {
                    throw new RuntimeException("A data de término não pode ser antes da data de início.");
                }
                curso.setDataFim(dtFim);
            }
        } catch (DateTimeParseException | IllegalArgumentException e) {
            // IllegalArgumentException já cobre NumberFormatException e DiaSemana.valueOf inválido
            throw new RuntimeException("Dados inválidos no formulário. Confira números, datas, horários e dias da semana.");
        }

        curso.setDataCadastro(LocalDate.now());
        curso.setStatus(StatusCurso.DISPONIVEL);
        curso.setAtivo(true);
        curso.setProfessorId(professorLogadoProvider.obterProfessorIdAtual());
        curso.setProfessorNome(professorLogadoProvider.obterProfessorNomeAtual());
        Usuario professor = sessaoService.usuarioLogado();
        if (professor != null) {
            curso.setProfessorFotoPerfil(professor.getFotoPerfil());
        }

        return curso;
    }

    public Curso cadastrarCurso(String nome, String descricao, String cargaHoraria,
                                String preco, String vagasTotais, List<String> diasSemana,
                                String horaInicio, String horaFim,
                                String dataInicio, String dataFim, String urlImagem) {

        Curso curso = criarCurso(nome, descricao, cargaHoraria, preco, vagasTotais,
                diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);

        if (existeConflitoParaProfessor(curso)) {
            throw new RuntimeException("Já existe um curso seu nesse horário.");
        }

        return cursoRepository.save(curso);
    }

    // =========================================================================
    // Exclusão (RN025)
    // =========================================================================

    /**
     * Só o professor dono (ou um administrador) exclui. RN025: não exclui se
     * houver aluno matriculado (aceito/aguardando pagamento/ativo). Solicitações
     * ainda pendentes são marcadas como recusadas pra não ficarem órfãs.
     * As matrículas antigas (canceladas/recusadas) permanecem no histórico.
     */
    public void excluirCurso(String cursoId) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new RuntimeException("Curso não encontrado"));

        boolean ehAdmin = "ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name());
        boolean ehDono = logado.getId().equals(curso.getProfessorId());
        if (!ehAdmin && !ehDono) {
            throw new RuntimeException("Você não tem permissão para excluir este curso.");
        }

        List<Matricula> matriculas = matriculaRepository.findByCursoId(cursoId);

        boolean temAlunoMatriculado = matriculas.stream()
                .anyMatch(m -> m.getStatusMatricula() != null && m.getStatusMatricula().ocupaVaga());
        if (temAlunoMatriculado) {
            throw new RuntimeException("Não é possível excluir um curso que possui alunos matriculados.");
        }

        List<Matricula> pendentes = matriculas.stream()
                .filter(m -> m.getStatusMatricula() == StatusMatricula.PENDENTE)
                .toList();
        pendentes.forEach(m -> m.setStatusMatricula(StatusMatricula.RECUSADA));
        if (!pendentes.isEmpty()) {
            matriculaRepository.saveAll(pendentes);
        }

        cursoRepository.delete(curso);
    }
}