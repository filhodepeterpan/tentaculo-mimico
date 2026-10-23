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
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CursoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    // Janela em que os cursos podem acontecer: nenhuma aula começa antes das 07:00
    // nem termina depois das 22:00.
    private static final LocalTime JANELA_INICIO = LocalTime.of(7, 0);
    private static final LocalTime JANELA_FIM = LocalTime.of(22, 0);

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

    // ETAPA 2: acha o primeiro bloco de horário de A que bate com algum de B
    // (devolve o bloco de B, ou null se não há sobreposição). Só olha o relógio e o dia
    // da semana; os períodos (datas) são conferidos antes, em cursosConflitam.
    private Horario horarioEmConflito(Curso a, Curso b) {
        if (a.getHorarios() == null || b.getHorarios() == null) {
            return null;
        }
        for (Horario ha : a.getHorarios()) {
            for (Horario hb : b.getHorarios()) {
                if (horariosSeSobrepoem(ha, hb)) {
                    return hb;
                }
            }
        }
        return null;
    }

    // ETAPA 3: algum curso (de qualquer professor) ocupa esse horário durante o
    // mesmo período? Se sim, lança a mensagem explicando qual, quando e até quando.
    // Cursos com períodos que não se cruzam (um termina antes do outro começar)
    // NÃO conflitam, mesmo no mesmo dia e hora.
    private void validarSemConflito(Curso cursoNovo) {
        List<String> conflitos = new ArrayList<>();

        for (Curso existente : cursoRepository.findAll()) {
            // Na edição, o próprio curso já está no banco: não conflita consigo mesmo.
            if (existente.getId() != null && existente.getId().equals(cursoNovo.getId())) {
                continue;
            }
            if (!periodosSeSobrepoem(cursoNovo, existente)) {
                continue;
            }
            Horario hb = horarioEmConflito(cursoNovo, existente);
            if (hb != null) {
                conflitos.add(descreverConflito(existente, hb));
            }
        }

        if (!conflitos.isEmpty()) {
            String mensagem = "Esse horário já está ocupado pelo curso " + conflitos.get(0) + ".";
            if (conflitos.size() > 1) {
                mensagem += " Há mais " + (conflitos.size() - 1) + " curso(s) em conflito.";
            }
            mensagem += " Para usar o mesmo dia e horário, ajuste as datas do seu curso para que"
                    + " ele comece depois do término do outro (ou termine antes do início dele),"
                    + " ou escolha outro horário.";
            throw new RuntimeException(mensagem);
        }
    }

    // Ex.: "Inglês Avançado (terças, 11:00 às 13:00, de 21/02/2026 a indeterminado)"
    private String descreverConflito(Curso existente, Horario h) {
        DateTimeFormatter dia = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter hora = DateTimeFormatter.ofPattern("HH:mm");
        String inicio = existente.getDataInicio() != null ? existente.getDataInicio().format(dia) : "?";
        String fim = existente.getDataFim() != null ? existente.getDataFim().format(dia) : "indeterminado";
        return "\"" + existente.getNome() + "\" ("
                + nomeDiaPlural(h.getDiaSemana()) + ", "
                + h.getInicio().format(hora) + " às " + h.getFim().format(hora)
                + ", de " + inicio + " a " + fim + ")";
    }

    private String nomeDiaPlural(DiaSemana dia) {
        return switch (dia.name()) {
            case "SEGUNDA" -> "segundas";
            case "TERCA" -> "terças";
            case "QUARTA" -> "quartas";
            case "QUINTA" -> "quintas";
            case "SEXTA" -> "sextas";
            case "SABADO" -> "sábados";
            case "DOMINGO" -> "domingos";
            default -> dia.name().toLowerCase();
        };
    }

    // =========================================================================
    // Cadastro
    // =========================================================================

    // A primeira aula (data de início no horário de início) não pode estar no passado.
    // Como a data de início já foi conferida contra os dias da semana, ela é mesmo um dia de aula.
    private void validarPrimeiraAulaNoFuturo(Curso curso) {
        if (curso.getDataInicio() == null || curso.getHorarios() == null || curso.getHorarios().isEmpty()) {
            return;
        }
        LocalDateTime primeiraAula = curso.getDataInicio().atTime(curso.getHorarios().get(0).getInicio());
        if (primeiraAula.isBefore(LocalDateTime.now(FUSO))) {
            throw new RuntimeException("A primeira aula ("
                    + primeiraAula.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))
                    + ") já passou. Escolha um dia e um horário a partir de agora.");
        }
    }

    // O curso já começou? (a primeira aula, no horário salvo, já passou)
    private boolean jaComecou(Curso curso) {
        if (curso.getDataInicio() == null || curso.getHorarios() == null || curso.getHorarios().isEmpty()) {
            return false;
        }
        return curso.getDataInicio().atTime(curso.getHorarios().get(0).getInicio())
                .isBefore(LocalDateTime.now(FUSO));
    }

    // Dois cursos têm a mesma agenda quando começam no mesmo dia e têm os mesmos dias e horários.
    // Na edição de um curso que ainda não começou, só quem muda a agenda passa pela regra do passado.
    private boolean mesmaAgenda(Curso a, Curso b) {
        return Objects.equals(a.getDataInicio(), b.getDataInicio())
                && chavesDeHorario(a).equals(chavesDeHorario(b));
    }

    private Set<String> chavesDeHorario(Curso curso) {
        if (curso.getHorarios() == null) {
            return Set.of();
        }
        return curso.getHorarios().stream()
                .map(h -> h.getDiaSemana() + "|" + h.getInicio() + "|" + h.getFim())
                .collect(Collectors.toSet());
    }

    // A data cai num dos dias da semana escolhidos? Ex.: curso só às terças e quintas
    // não pode começar (nem terminar) num sábado.
    private void validarDataNosDias(String qual, LocalDate data, List<String> diasSemana) {
        String diaDaData = switch (data.getDayOfWeek()) {
            case MONDAY -> "SEGUNDA";
            case TUESDAY -> "TERCA";
            case WEDNESDAY -> "QUARTA";
            case THURSDAY -> "QUINTA";
            case FRIDAY -> "SEXTA";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
        if (!diasSemana.contains(diaDaData)) {
            String dias = diasSemana.stream()
                    .map(d -> nomeDiaPlural(DiaSemana.valueOf(d)))
                    .collect(Collectors.joining(", "));
            throw new RuntimeException("A data de " + qual + " ("
                    + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + ", " + nomeDiaPlural(DiaSemana.valueOf(diaDaData)).replaceFirst("s$", "")
                    + ") precisa cair em um dos dias do curso: " + dias + ".");
        }
    }

    // Lê e valida os campos do formulário (cadastro e edição usam o mesmo).
    // Não mexe em dono, status nem datas de cadastro: isso é de quem chama.
    private Curso lerFormulario(String nome, String descricao, String cargaHoraria,
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
            if (inicio.isBefore(JANELA_INICIO) || fim.isAfter(JANELA_FIM)) {
                throw new RuntimeException("O horário do curso deve ficar entre 07:00 e 22:00.");
            }

            List<Horario> horarios = new ArrayList<>();
            for (String dia : diasSemana) {
                horarios.add(new Horario(DiaSemana.valueOf(dia), inicio, fim));
            }
            curso.setHorarios(horarios);

            LocalDate dtInicio = LocalDate.parse(dataInicio);
            curso.setDataInicio(dtInicio);
            if (dataFim == null || dataFim.isBlank()) {
                throw new RuntimeException("Informe a data de término do curso.");
            }
            LocalDate dtFim = LocalDate.parse(dataFim);
            if (dtFim.isBefore(dtInicio)) {
                throw new RuntimeException("A data de término não pode ser antes da data de início.");
            }
            // As duas datas precisam cair num dos dias da semana marcados no formulário.
            validarDataNosDias("início", dtInicio, diasSemana);
            validarDataNosDias("término", dtFim, diasSemana);
            curso.setDataFim(dtFim);
        } catch (DateTimeParseException | IllegalArgumentException e) {
            // IllegalArgumentException já cobre NumberFormatException e DiaSemana.valueOf inválido
            throw new RuntimeException("Dados inválidos no formulário. Confira números, datas, horários e dias da semana.");
        }

        return curso;
    }

    public Curso criarCurso(String nome, String descricao, String cargaHoraria,
                            String preco, String vagasTotais, List<String> diasSemana,
                            String horaInicio, String horaFim,
                            String dataInicio, String dataFim, String urlImagem) {

        Curso curso = lerFormulario(nome, descricao, cargaHoraria, preco, vagasTotais,
                diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);

        curso.setDataCadastro(LocalDate.now());
        curso.setStatus(StatusCurso.DISPONIVEL);
        curso.setAtivo(true);
        // O dono do curso é sempre quem está logado na sessão. Antes vinha do
        // ProfessorLogadoProvider, que devolvia um professor de teste fixo.
        Usuario professor = sessaoService.exigirUsuarioLogado();
        curso.setProfessorId(professor.getId());
        curso.setProfessorNome(professor.getNome());
        curso.setProfessorFotoPerfil(professor.getFotoPerfil());

        return curso;
    }

    public Curso cadastrarCurso(String nome, String descricao, String cargaHoraria,
                                String preco, String vagasTotais, List<String> diasSemana,
                                String horaInicio, String horaFim,
                                String dataInicio, String dataFim, String urlImagem) {

        Curso curso = criarCurso(nome, descricao, cargaHoraria, preco, vagasTotais,
                diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);

        validarPrimeiraAulaNoFuturo(curso);
        validarSemConflito(curso);

        return cursoRepository.save(curso);
    }

    // =========================================================================
    // Edição
    // =========================================================================

    /** Carrega o curso para edição, só se o usuário logado for o dono (ou admin). */
    public Curso buscarParaEdicao(String cursoId) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new RuntimeException("Curso não encontrado"));

        boolean ehAdmin = "ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name());
        boolean ehDono = logado.getId().equals(curso.getProfessorId());
        if (!ehAdmin && !ehDono) {
            throw new RuntimeException("Você não tem permissão para editar este curso.");
        }
        return curso;
    }

    /**
     * Atualiza os dados do curso. Dono, status e data de cadastro não mudam.
     * A imagem de capa só é trocada se vier uma nova (urlImagem != null).
     * Vagas: o total não pode ficar menor que as vagas já ocupadas, e as
     * disponíveis são recalculadas a partir disso.
     */
    public Curso atualizarCurso(String cursoId, String nome, String descricao, String cargaHoraria,
                                String preco, String vagasTotais, List<String> diasSemana,
                                String horaInicio, String horaFim,
                                String dataInicio, String dataFim, String urlImagem) {

        Curso curso = buscarParaEdicao(cursoId);
        Curso editado = lerFormulario(nome, descricao, cargaHoraria, preco, vagasTotais,
                diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);

        int vagasOcupadas = curso.getVagasTotais() - curso.getVagasDisponiveis();
        if (editado.getVagasTotais() < vagasOcupadas) {
            throw new RuntimeException("Este curso já tem " + vagasOcupadas
                    + " vaga(s) ocupada(s); o total de vagas não pode ser menor que isso.");
        }

        if (jaComecou(curso)) {
            // Curso em andamento: dias e horário podem mudar, mas as datas de início e de
            // término ficam como estão.
            if (!Objects.equals(curso.getDataInicio(), editado.getDataInicio())
                    || !Objects.equals(curso.getDataFim(), editado.getDataFim())) {
                throw new RuntimeException("Este curso já começou: as datas de início e de término não podem ser alteradas.");
            }
        } else if (!mesmaAgenda(curso, editado)) {
            // Curso que ainda não começou: se mudar a data, os dias ou o horário, a primeira
            // aula continua precisando estar no futuro.
            validarPrimeiraAulaNoFuturo(editado);
        }

        curso.setNome(editado.getNome());
        curso.setDescricao(editado.getDescricao());
        curso.setCargaHoraria(editado.getCargaHoraria());
        curso.setPreco(editado.getPreco());
        curso.setVagasTotais(editado.getVagasTotais());
        curso.setVagasDisponiveis(editado.getVagasTotais() - vagasOcupadas);
        curso.setHorarios(editado.getHorarios());
        curso.setDataInicio(editado.getDataInicio());
        curso.setDataFim(editado.getDataFim());
        if (urlImagem != null) {
            curso.setImagemCapa(urlImagem);
        }

        validarSemConflito(curso);

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

        List<Matricula> matriculas = ObjectId.isValid(cursoId) ? matriculaRepository.findByCursoId(cursoId) : List.of();

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