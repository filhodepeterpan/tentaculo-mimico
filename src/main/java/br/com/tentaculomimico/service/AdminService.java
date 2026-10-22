package br.com.tentaculomimico.service;

import br.com.tentaculomimico.dto.ResumoAdminResposta;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.MatriculaRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final MatriculaRepository matriculaRepository;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;

    public AdminService(UsuarioRepository usuarioRepository,
                        CursoRepository cursoRepository,
                        MatriculaRepository matriculaRepository,
                        MatriculaService matriculaService,
                        SessaoService sessaoService) {
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.matriculaRepository = matriculaRepository;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
    }

    public ResumoAdminResposta resumo() {
        return new ResumoAdminResposta(
                usuarioRepository.countByTipoUsuario(TipoUsuario.ALUNO),
                usuarioRepository.countByTipoUsuario(TipoUsuario.PROFESSOR),
                cursoRepository.countByAtivoTrue(),
                matriculaRepository.count()
        );
    }


    //===============================================================================


    public List<AlunoAdminResposta> listarAlunos() {
        List<AlunoAdminResposta> resultado = new ArrayList<>();

        for (Usuario aluno : usuarioRepository.findByTipoUsuario(TipoUsuario.ALUNO)) {

            List<Matricula> matriculas = matriculaRepository.findByAlunoId(aluno.getId());
            List<Curso> cursos = new ArrayList<>();

            for (Matricula m : matriculas) {
                Curso curso = cursoRepository.findById(m.getCursoId()).orElse(null);
                if (curso != null) {
                    cursos.add(curso);
                }
            }
            resultado.add(new AlunoAdminResposta(aluno.getId(), aluno.getNome(), aluno.getEmail(),cursos, aluno.getFotoPerfil()));
        }

        return resultado;
    }


    //===============================================================================

    public List<ProfessorAdminResposta> listarProfessores() {
        List<ProfessorAdminResposta> resultado = new ArrayList<>();

        for (Usuario professor : usuarioRepository.findByTipoUsuario(TipoUsuario.PROFESSOR)) {
            List<Curso> cursos = cursoRepository.findByProfessorId(professor.getId());
            resultado.add(new ProfessorAdminResposta(professor.getId(), professor.getNome(), professor.getEmail(), cursos, professor.getFotoPerfil()));
        }

        return resultado;
    }
    //===============================================================================

    public List<CursoAdminResposta> listarCursosComAlunos() {
        List<CursoAdminResposta> resultado = new ArrayList<>();
        for (Curso c : cursoRepository.findAll()) {

            List<Matricula> matriculas;
            if (ObjectId.isValid(c.getId())) {
                matriculas = matriculaRepository.findByCursoId(c.getId());
            } else {
                matriculas = new ArrayList<>();
            }

            resultado.add(new CursoAdminResposta(c, matriculas));
        }
        return resultado;
    }
    //===============================================================================

    public void removerAluno(String alunoId) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        if (logado.getTipoUsuario() != TipoUsuario.ADMINISTRADOR) {
            throw new RuntimeException("Acesso liberado apenas para administradores");
        }
        if (logado.getId().equals(alunoId)) {
            throw new RuntimeException("Não é possivel deletar a conta de administrador");
        }

        Usuario aluno = usuarioRepository.findById(alunoId)
                .orElseThrow(() -> new RuntimeException("Aluno não encontrado."));
        if (aluno.getTipoUsuario() != TipoUsuario.ALUNO) {
            throw new RuntimeException("O usuário informado não é um aluno.");
        }

        matriculaService.cancelarEmAbertoDoAluno(alunoId);
        usuarioRepository.delete(aluno);
    }

    //===============================================================================
    public List<Curso> listarCursos() {
        return cursoRepository.findAll();
    }
}