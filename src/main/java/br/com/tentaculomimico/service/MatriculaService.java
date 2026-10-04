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
    private final AlunoLogadoProvider alunoLogadoProvider;
    private final SessaoService sessaoService;
    private final UsuarioRepository usuarioRepository;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            CursoRepository cursoRepository,
                            AlunoLogadoProvider alunoLogadoProvider,
                            SessaoService sessaoService,
                            UsuarioRepository usuarioRepository) {
        this.matriculaRepository = matriculaRepository;
        this.cursoRepository = cursoRepository;
        this.alunoLogadoProvider = alunoLogadoProvider;
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

        String alunoId = alunoLogadoProvider.obterAlunoIdAtual();
        String alunoNome = alunoLogadoProvider.obterAlunoNomeAtual();

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
        // Pode haver uma CANCELADA antiga e uma nova: a que vale é a não cancelada;
        // se todas estiverem canceladas, a mais recente.
        Matricula atual = matriculas.stream()
                .filter(m -> m.getStatusMatricula() != StatusMatricula.CANCELADA)
                .findFirst()
                .orElseGet(() -> matriculas.stream()
                        .max(Comparator.comparing(Matricula::getHorarioMatricula,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .get());
        return paraView(atual, cursoRepository.findById(cursoId).orElse(null));
    }

    public List<MatriculaCursoView> listarCursosDoAluno(String alunoId) {
        return matriculaRepository.findByAlunoId(alunoId).stream()
                .map(m -> paraView(m, cursoRepository.findById(m.getCursoId()).orElse(null)))
                .toList();
    }

    /** Alunos com matrícula ATIVA, um item por aluno, com os cursos dele (deste professor) em texto. */
    public List<AlunoMatriculadoView> listarAlunosMatriculados(String professorId) {
        Map<String, Curso> cursos = cursosDoProfessor(professorId);
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

    /** Solicitações em processo: pendentes de aprovação e aprovadas aguardando pagamento. */
    public List<AlunoPendenteView> listarAlunosPendentes(String professorId) {
        Map<String, Curso> cursos = cursosDoProfessor(professorId);
        if (cursos.isEmpty()) {
            return List.of();
        }
        List<AlunoPendenteView> views = new ArrayList<>();
        for (Matricula m : matriculaRepository.findByCursoIdIn(cursos.keySet())) {
            StatusMatricula st = m.getStatusMatricula();
            if (st != StatusMatricula.PENDENTE && st != StatusMatricula.AGUARDANDO_PAGAMENTO) {
                continue;
            }
            Usuario aluno = usuarioRepository.findById(m.getAlunoId()).orElse(null);
            views.add(new AlunoPendenteView(
                    m.getAlunoId(),
                    aluno != null ? aluno.getNome() : m.getNomeAluno(),
                    aluno != null ? aluno.getFotoPerfil() : null,
                    aluno != null ? aluno.getEmail() : null,
                    m.getId(),
                    st == StatusMatricula.PENDENTE ? "Pendente de aprovação" : "Aguardando pagamento",
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