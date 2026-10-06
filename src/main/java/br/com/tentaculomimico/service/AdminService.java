package br.com.tentaculomimico.service;

import br.com.tentaculomimico.dto.ResumoAdminResposta;
import br.com.tentaculomimico.model.*;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.MatriculaRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final MatriculaRepository matriculaRepository;

    public AdminService(UsuarioRepository usuarioRepository,
                        CursoRepository cursoRepository,
                        MatriculaRepository matriculaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.matriculaRepository = matriculaRepository;
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
            resultado.add(new AlunoAdminResposta(aluno.getId(), aluno.getNome(), aluno.getEmail(), cursos));
        }

        return resultado;
    }


    //===============================================================================

    public List<ProfessorAdminResposta> listarProfessores() {
        List<ProfessorAdminResposta> resultado = new ArrayList<>();

        for (Usuario professor : usuarioRepository.findByTipoUsuario(TipoUsuario.PROFESSOR)) {
            List<Curso> cursos = cursoRepository.findByProfessorId(professor.getId());
            resultado.add(new ProfessorAdminResposta(professor.getId(), professor.getNome(), professor.getEmail(), cursos));
        }

        return resultado;
    }


    //===============================================================================
    public List<Curso> listarCursos() {
        return cursoRepository.findAll();
    }
}