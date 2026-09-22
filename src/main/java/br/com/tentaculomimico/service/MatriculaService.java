package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusCurso;
import br.com.tentaculomimico.repository.MatriculaRepository;
import br.com.tentaculomimico.repository.CursoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatriculaService {

    private final MatriculaRepository matriculaRepository;
    private final CursoRepository cursoRepository;
    private final AlunoLogadoProvider alunoLogadoProvider;

    public MatriculaService(MatriculaRepository matriculaRepository,
                            CursoRepository cursoRepository,
                            AlunoLogadoProvider alunoLogadoProvider) {
        this.matriculaRepository = matriculaRepository;
        this.cursoRepository = cursoRepository;
        this.alunoLogadoProvider = alunoLogadoProvider;
    }

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

        List<Matricula> matriculasExistentes = matriculaRepository.findByAlunoIdAndCursoId(alunoId, cursoId);
        if (!matriculasExistentes.isEmpty()) {
            throw new RuntimeException("Matricula já registrada anteriormente");
        }

        matriculaRepository.save(new Matricula(cursoId, alunoId, alunoNome));
    }
}