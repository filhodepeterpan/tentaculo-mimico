package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusCurso;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.view.AlunoMatriculadoView;
import br.com.tentaculomimico.model.view.AlunoPendenteView;
import br.com.tentaculomimico.model.view.MatriculaCursoView;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.MatriculaRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final CursoRepository cursoRepository;
    private final SessaoService sessaoService;
    private final UsuarioRepository usuarioRepository;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            CursoRepository cursoRepository,
                            SessaoService sessaoService,
                            UsuarioRepository usuarioRepository) {
        this.matriculaRepository = matriculaRepository;
        this.cursoRepository = cursoRepository;
        this.sessaoService = sessaoService;
        this.usuarioRepository = usuarioRepository;
    }

    // =========================================================================
    // Ações
    // =========================================================================

    public void solicitarMatricula(String cursoId) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new RuntimeException("Curso não encontrado"));

        if (curso.getStatus() != StatusCurso.DISPONIVEL) {
            throw new RuntimeException("Curso não disponível no momento");
        }

        if (curso.getVagasDisponiveis() < 1) {
            throw new RuntimeException("Curso lotado");
        }

        // Aluno = usuário realmente logado (o mesmo id que o perfil-aluno consulta depois).
        Usuario aluno = sessaoService.exigirUsuarioLogado();
        String alunoId = aluno.getId();
        String alunoNome = aluno.getNome();

        // Matrículas CANCELADAS não impedem uma nova solicitação (o histórico fica preservado).
        List<Matricula> matriculasExistentes = matriculaRepository.findByAlunoIdAndCursoId(alunoId, cursoId);
        boolean jaTemMatricula = matriculasExistentes.stream()
                .anyMatch(m -> m.getStatusMatricula() != StatusMatricula.CANCELADA);
        if (jaTemMatricula) {
            throw new RuntimeException("Matricula já registrada anteriormente");
        }

        // o construtor de Matricula já define status PENDENTE e horarioMatricula
        matriculaRepository.save(new Matricula(cursoId, alunoId, alunoNome));
    }

    /** Professor aceita: reserva a vaga e fica aguardando o pagamento. Devolve o cursoId. */
    public String aceitar(String matriculaId) {
        Matricula matricula = buscarMatricula(matriculaId);
        Curso curso = buscarCurso(matricula.getCursoId());
        exigirProfessorDoCurso(curso);

        if (matricula.getStatusMatricula() != StatusMatricula.PENDENTE) {
            throw new RuntimeException("Esta solicitação não está mais pendente.");
        }
        if (curso.getVagasDisponiveis() < 1) {
            throw new RuntimeException("Curso lotado");
        }

        curso.setVagasDisponiveis(curso.getVagasDisponiveis() - 1);
        matricula.setStatusMatricula(StatusMatricula.AGUARDANDO_PAGAMENTO);
        matricula.setDataRespostaProfessor(LocalDateTime.now());

        cursoRepository.save(curso);
        matriculaRepository.save(matricula);
        return curso.getId();
    }

    /** Professor recusa a solicitação pendente. Devolve o cursoId. */
    public String recusar(String matriculaId) {
        Matricula matricula = buscarMatricula(matriculaId);
        Curso curso = buscarCurso(matricula.getCursoId());
        exigirProfessorDoCurso(curso);

        if (matricula.getStatusMatricula() != StatusMatricula.PENDENTE) {
            throw new RuntimeException("Esta solicitação não está mais pendente.");
        }

        matricula.setStatusMatricula(StatusMatricula.RECUSADA);
        matricula.setDataRespostaProfessor(LocalDateTime.now());
        matriculaRepository.save(matricula);
        return curso.getId();
    }

    /**
     * Matrícula que o aluno logado está pagando. Lança se não existir ou se não for dele
     * (impede abrir /pagamentos/{id} de outra pessoa).
     */
    public Matricula buscarMatriculaDoAlunoLogado(String matriculaId) {
        Matricula matricula = buscarMatricula(matriculaId);
        Usuario logado = sessaoService.exigirUsuarioLogado();
        if (!logado.getId().equals(matricula.getAlunoId())) {
            throw new RuntimeException("Você não tem permissão para acessar esta matrícula.");
        }
        return matricula;
    }

    /**
     * Pagamento concluído: AGUARDANDO_PAGAMENTO vira ATIVA e libera o acesso (o aluno passa a
     * aparecer em "alunos matriculados" do professor). Devolve o cursoId.
     *
     * ATENÇÃO, PROVISÓRIO: ainda não existe gateway de pagamento, então este método CONFIA no clique
     * do aluno. Quando houver gateway, quem chama isto deve ser o webhook de "pagamento aprovado",
     * e o endpoint do aluno deve deixar de existir (ver PagamentoController).
     * Idempotente: chamar de novo numa matrícula já ATIVA não faz nada (duplo clique, reenvio).
     */
    public String confirmarPagamento(String matriculaId) {
        Matricula matricula = buscarMatriculaDoAlunoLogado(matriculaId);

        if (matricula.getStatusMatricula() == StatusMatricula.ATIVA) {
            return matricula.getCursoId();
        }
        if (matricula.getStatusMatricula() != StatusMatricula.AGUARDANDO_PAGAMENTO) {
            throw new RuntimeException("Esta matrícula não está aguardando pagamento.");
        }

        matricula.setStatusMatricula(StatusMatricula.ATIVA);
        matricula.setAcessoLiberado(true);
        matriculaRepository.save(matricula);
        return matricula.getCursoId();
    }

    /** Aluno desiste (RN039/RN044): vira CANCELADA, o registro fica no histórico. */
    public void cancelar(String matriculaId) {
        Matricula matricula = buscarMatricula(matriculaId);
        Usuario logado = sessaoService.exigirUsuarioLogado();

        if (!logado.getId().equals(matricula.getAlunoId())) {
            throw new RuntimeException("Você não tem permissão para cancelar esta matrícula.");
        }
        if (matricula.getStatusMatricula() == null || !matricula.getStatusMatricula().emAberto()) {
            throw new RuntimeException("Esta matrícula não pode mais ser cancelada.");
        }
        cancelarInterno(matricula);
    }

    /**
     * Professor cancela matrículas ATIVAS dos alunos dos cursos dele (uma ou várias de uma vez,
     * conforme a escolha no modal). Vira CANCELADA, o histórico fica e a vaga é devolvida.
     * Valida TODAS antes de cancelar qualquer uma, para não cancelar só metade se uma falhar.
     * Devolve o cursoId da primeira matrícula (usado no redirect de volta para o curso).
     */
    public String cancelarPeloProfessor(List<String> matriculaIds) {
        if (matriculaIds == null || matriculaIds.isEmpty()) {
            throw new RuntimeException("Selecione a matrícula que deseja cancelar.");
        }

        List<Matricula> matriculas = new ArrayList<>();
        for (String id : matriculaIds.stream().distinct().toList()) {
            Matricula matricula = buscarMatricula(id);
            exigirProfessorDoCurso(buscarCurso(matricula.getCursoId()));
            if (matricula.getStatusMatricula() != StatusMatricula.ATIVA) {
                throw new RuntimeException("Só é possível cancelar matrículas ativas.");
            }
            matriculas.add(matricula);
        }

        matriculas.forEach(this::cancelarInterno);
        return matriculas.get(0).getCursoId();
    }

    /** Uso interno (ex.: exclusão de conta do aluno): cancela tudo que está em aberto, sem checar sessão. */
    void cancelarEmAbertoDoAluno(String alunoId) {
        matriculaRepository.findByAlunoId(alunoId).stream()
                .filter(m -> m.getStatusMatricula() != null && m.getStatusMatricula().emAberto())
                .forEach(this::cancelarInterno);
    }

    /**
     * Exclusão de conta do ALUNO: só PENDENTE pode ficar (é cancelada junto com a conta).
     * Matrícula ATIVA ou AGUARDANDO_PAGAMENTO bloqueia: o aluno precisa desistir antes.
     * Lança RuntimeException com o motivo (o controller mostra num modal).
     */
    public void exigirAlunoPodeExcluirConta(String alunoId) {
        List<Matricula> ocupando = matriculaRepository.findByAlunoId(alunoId).stream()
                .filter(m -> m.getStatusMatricula() != null && m.getStatusMatricula().ocupaVaga())
                .toList();
        if (ocupando.isEmpty()) {
            return;
        }

        Map<String, Curso> nomes = new LinkedHashMap<>();
        ocupando.forEach(m -> nomes.computeIfAbsent(m.getCursoId(),
                id -> cursoRepository.findById(id).orElse(null)));

        String ativas = nomesDosCursos(ocupando, StatusMatricula.ATIVA, nomes);
        String aguardando = nomesDosCursos(ocupando, StatusMatricula.AGUARDANDO_PAGAMENTO, nomes);

        StringBuilder msg = new StringBuilder();
        if (!ativas.isEmpty()) {
            msg.append("Você tem matrícula ativa em: ").append(ativas).append(". ");
        }
        if (!aguardando.isEmpty()) {
            msg.append("Você tem matrícula aguardando pagamento em: ").append(aguardando).append(". ");
        }
        msg.append("Desista de cada uma na página do curso e depois tente excluir a conta de novo.");
        throw new RuntimeException(msg.toString());
    }

    /**
     * Exclusão de conta do PROFESSOR: bloqueia se algum curso dele tem aluno com matrícula
     * ATIVA (ele precisa cancelar) ou AGUARDANDO_PAGAMENTO (ele precisa esperar o aluno pagar
     * ou desistir, e só então cancelar). Pendentes não bloqueiam: viram recusadas na exclusão do curso.
     */
    public void exigirProfessorPodeExcluirConta(String professorId) {
        Map<String, Curso> cursos = cursosDoProfessor(professorId);
        if (cursos.isEmpty()) {
            return;
        }
        List<Matricula> ocupando = matriculaRepository.findByCursoIdIn(cursos.keySet()).stream()
                .filter(m -> m.getStatusMatricula() != null && m.getStatusMatricula().ocupaVaga())
                .toList();
        if (ocupando.isEmpty()) {
            return;
        }

        String ativas = nomesDosCursos(ocupando, StatusMatricula.ATIVA, cursos);
        String aguardando = nomesDosCursos(ocupando, StatusMatricula.AGUARDANDO_PAGAMENTO, cursos);

        StringBuilder msg = new StringBuilder();
        if (!ativas.isEmpty()) {
            msg.append("Há alunos matriculados em: ").append(ativas)
               .append(". Cancele a matrícula deles (na aba \"Alunos matriculados\" ou na página do curso). ");
        }
        if (!aguardando.isEmpty()) {
            msg.append("Há alunos aguardando pagamento em: ").append(aguardando)
               .append(". Aguarde o pagamento ou a desistência do aluno");
        }
        msg.append("Só então será possível excluir a conta.");
        throw new RuntimeException(msg.toString());
    }

    private String nomesDosCursos(List<Matricula> matriculas, StatusMatricula status, Map<String, Curso> cursos) {
        return matriculas.stream()
                .filter(m -> m.getStatusMatricula() == status)
                .map(m -> {
                    Curso c = cursos.get(m.getCursoId());
                    return c != null ? c.getNome() : "(curso removido)";
                })
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private void cancelarInterno(Matricula matricula) {
        boolean ocupavaVaga = matricula.getStatusMatricula().ocupaVaga();
        matricula.setStatusMatricula(StatusMatricula.CANCELADA);
        matricula.setAcessoLiberado(false);
        matricula.setDataCancelamento(LocalDateTime.now());
        matriculaRepository.save(matricula);

        if (ocupavaVaga) {
            cursoRepository.findById(matricula.getCursoId()).ifPresent(curso -> {
                int vagas = Math.min(curso.getVagasDisponiveis() + 1, curso.getVagasTotais());
                curso.setVagasDisponiveis(vagas);
                cursoRepository.save(curso);
            });
        }
    }

    // =========================================================================
    // Consultas (viram atributos dos templates)
    // =========================================================================

    /** Matrícula do aluno nesse curso, ou null se nunca solicitou. */
    public MatriculaCursoView buscarStatusDoAluno(String alunoId, String cursoId) {
        List<Matricula> matriculas = matriculaRepository.findByAlunoIdAndCursoId(alunoId, cursoId);
        if (matriculas.isEmpty()) {
            return null;
        }
        return paraView(matriculaAtual(matriculas), cursoRepository.findById(cursoId).orElse(null));
    }

    /**
     * Matrícula "atual" do aluno em cada curso (chave = cursoId), numa única consulta.
     * Serve para a listagem de cursos decidir o botão de cada card. As views só trazem
     * id e status preenchidos de verdade (nome/carga/capa do curso não são carregados aqui).
     */
    public Map<String, MatriculaCursoView> mapaMatriculasDoAluno(String alunoId) {
        Map<String, List<Matricula>> porCurso = matriculaRepository.findByAlunoId(alunoId).stream()
                .collect(Collectors.groupingBy(Matricula::getCursoId));

        Map<String, MatriculaCursoView> mapa = new LinkedHashMap<>();
        porCurso.forEach((cursoId, lista) -> mapa.put(cursoId, paraView(matriculaAtual(lista), null)));
        return mapa;
    }

    // Pode haver uma CANCELADA antiga e uma nova: a que vale é a não cancelada;
    // se todas estiverem canceladas, a mais recente.
    private Matricula matriculaAtual(List<Matricula> matriculas) {
        return matriculas.stream()
                .filter(m -> m.getStatusMatricula() != StatusMatricula.CANCELADA)
                .findFirst()
                .orElseGet(() -> matriculas.stream()
                        .max(Comparator.comparing(Matricula::getHorarioMatricula,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .get());
    }

    /**
     * "Meus cursos" do perfil do aluno. Matrículas CANCELADAS (desistência do aluno ou
     * cancelamento pelo professor) ficam só no histórico do banco e não aparecem aqui:
     * para o aluno, é como se nunca tivesse se matriculado.
     */
    public List<MatriculaCursoView> listarCursosDoAluno(String alunoId) {
        return matriculaRepository.findByAlunoId(alunoId).stream()
                .filter(m -> m.getStatusMatricula() != StatusMatricula.CANCELADA)
                .map(m -> paraView(m, cursoRepository.findById(m.getCursoId()).orElse(null)))
                .toList();
    }

    /** Alunos com matrícula ATIVA, um item por aluno, com os cursos dele (deste professor) em texto. */
    public List<AlunoMatriculadoView> listarAlunosMatriculados(String professorId) {
        return montarAlunosMatriculados(cursosDoProfessor(professorId));
    }

    /**
     * Solicitações pendentes de aprovação do professor. Depois que ele aceita (AGUARDANDO_PAGAMENTO)
     * ou recusa, a solicitação sai desta lista e o contador de notificação diminui.
     */
    public List<AlunoPendenteView> listarAlunosPendentes(String professorId) {
        return montarAlunosPendentes(cursosDoProfessor(professorId));
    }

    /** Igual a listarAlunosMatriculados, mas só deste curso. Só o professor dono do curso pode chamar. */
    public List<AlunoMatriculadoView> listarAlunosMatriculadosDoCurso(String cursoId) {
        Curso curso = buscarCurso(cursoId);
        exigirProfessorDoCurso(curso);
        return montarAlunosMatriculados(Map.of(curso.getId(), curso));
    }

    /** Igual a listarAlunosPendentes, mas só deste curso. Só o professor dono do curso pode chamar. */
    public List<AlunoPendenteView> listarAlunosPendentesDoCurso(String cursoId) {
        Curso curso = buscarCurso(cursoId);
        exigirProfessorDoCurso(curso);
        return montarAlunosPendentes(Map.of(curso.getId(), curso));
    }

    private List<AlunoMatriculadoView> montarAlunosMatriculados(Map<String, Curso> cursos) {
        if (cursos.isEmpty()) {
            return List.of();
        }
        Map<String, List<Matricula>> porAluno = matriculaRepository.findByCursoIdIn(cursos.keySet()).stream()
                .filter(m -> m.getStatusMatricula() == StatusMatricula.ATIVA)
                .collect(Collectors.groupingBy(Matricula::getAlunoId, LinkedHashMap::new, Collectors.toList()));

        List<AlunoMatriculadoView> views = new ArrayList<>();
        porAluno.forEach((alunoId, matriculas) -> {
            Usuario aluno = usuarioRepository.findById(alunoId).orElse(null);
            String nome = aluno != null ? aluno.getNome() : matriculas.get(0).getNomeAluno();
            String foto = aluno != null ? aluno.getFotoPerfil() : null;
            String cursosTexto = matriculas.stream()
                    .map(m -> cursos.get(m.getCursoId()).getNome())
                    .distinct()
                    .collect(Collectors.joining(", "));
            List<AlunoMatriculadoView.MatriculaItem> itens = matriculas.stream()
                    .map(m -> new AlunoMatriculadoView.MatriculaItem(m.getId(), cursos.get(m.getCursoId()).getNome()))
                    .toList();
            views.add(new AlunoMatriculadoView(alunoId, nome, foto, cursosTexto, itens));
        });
        return views;
    }

    private List<AlunoPendenteView> montarAlunosPendentes(Map<String, Curso> cursos) {
        if (cursos.isEmpty()) {
            return List.of();
        }
        List<AlunoPendenteView> views = new ArrayList<>();
        for (Matricula m : matriculaRepository.findByCursoIdIn(cursos.keySet())) {
            if (m.getStatusMatricula() != StatusMatricula.PENDENTE) {
                continue;
            }
            Usuario aluno = usuarioRepository.findById(m.getAlunoId()).orElse(null);
            views.add(new AlunoPendenteView(
                    m.getAlunoId(),
                    aluno != null ? aluno.getNome() : m.getNomeAluno(),
                    aluno != null ? aluno.getFotoPerfil() : null,
                    aluno != null ? aluno.getEmail() : null,
                    m.getId(),
                    "Pendente de aprovação",
                    cursos.get(m.getCursoId()).getNome()));
        }
        return views;
    }

    // Usados pelo MatriculaController pra montar os redirects
    public String obterAlunoDaMatricula(String matriculaId) {
        return buscarMatricula(matriculaId).getAlunoId();
    }

    public String obterProfessorDoCurso(String cursoId) {
        return buscarCurso(cursoId).getProfessorId();
    }

    // =========================================================================
    // Auxiliares
    // =========================================================================

    private Map<String, Curso> cursosDoProfessor(String professorId) {
        return cursoRepository.findByProfessorId(professorId).stream()
                .collect(Collectors.toMap(Curso::getId, Function.identity()));
    }

    private MatriculaCursoView paraView(Matricula m, Curso curso) {
        return new MatriculaCursoView(
                m.getId(),
                m.getCursoId(),
                curso != null ? curso.getNome() : "(curso removido)",
                curso != null ? curso.getCargaHoraria() : 0,
                curso != null ? curso.getImagemCapa() : null,
                m.getStatusMatricula() != null ? m.getStatusMatricula().chave() : null);
    }

    private Matricula buscarMatricula(String id) {
        return matriculaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Matrícula não encontrada"));
    }

    private Curso buscarCurso(String id) {
        return cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso não encontrado"));
    }

    private void exigirProfessorDoCurso(Curso curso) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        if (!logado.getId().equals(curso.getProfessorId())) {
            throw new RuntimeException("Você não tem permissão para gerenciar as matrículas deste curso.");
        }
    }
}