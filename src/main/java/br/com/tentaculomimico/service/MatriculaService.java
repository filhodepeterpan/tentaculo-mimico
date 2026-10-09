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

    /** Uso interno (ex.: exclusão de conta do aluno): cancela tudo que está em aberto, sem checar sessão. */
    void cancelarEmAbertoDoAluno(String alunoId) {
        matriculaRepository.findByAlunoId(alunoId).stream()
                .filter(m -> m.getStatusMatricula() != null && m.getStatusMatricula().emAberto())
                .forEach(this::cancelarInterno);
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

    public List<MatriculaCursoView> listarCursosDoAluno(String alunoId) {
        return matriculaRepository.findByAlunoId(alunoId).stream()
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
            views.add(new AlunoMatriculadoView(alunoId, nome, foto, cursosTexto));
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