package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.service.sessao.SessaoService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final CursoService cursoService;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;
    private final ImagemService imagemService;

    public PerfilService(
        UsuarioRepository usuarioRepository,
        CursoRepository cursoRepository,
        CursoService cursoService,
        MatriculaService matriculaService,
        SessaoService sessaoService,
        ImagemService imagemService
    ) {
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.cursoService = cursoService;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
        this.imagemService = imagemService;
    }

    public void atualizarDadosPessoais(String nome, MultipartFile fotoPerfil) {
        Usuario usuario = sessaoService.exigirUsuarioLogado();

        if (nome == null || nome.isBlank()) {
            throw new RuntimeException("O nome não pode ficar em branco.");
        }
        usuario.setNome(nome.trim());

        String urlFoto = imagemService.enviar(fotoPerfil, "perfis");
        if (urlFoto != null) {
            // sem arquivo novo, mantém a foto atual
            usuario.setFotoPerfil(urlFoto);
        }

        usuarioRepository.save(usuario);
    }

    /**
     * ATENÇÃO: regras de negócio abaixo são uma interpretação de RN011/012/019
     * (não tenho o texto delas) — ajuste se divergirem:
     *  - Professor: não exclui se algum curso dele tem aluno matriculado
     *    (CursoService.excluirCurso lança a exceção); os demais cursos são excluídos.
     *  - Aluno: matrículas em aberto são canceladas (vagas devolvidas).
     * Limitação: se o professor tiver vários cursos e um deles bloquear, os
     *    anteriores já foram excluídos (sem transação). Se isso importar, crie
     *    um método "podeExcluir" no CursoService e valide todos antes.
     */
    public void excluirContaDoUsuarioLogado() {
        Usuario usuario = sessaoService.exigirUsuarioLogado();
        String tipo = usuario.getTipoUsuario().name();

        if ("PROFESSOR".equalsIgnoreCase(tipo)) {
            for (Curso curso : cursoRepository.findByProfessorId(
                usuario.getId()
            )) {
                cursoService.excluirCurso(curso.getId());
            }
        } else if ("ALUNO".equalsIgnoreCase(tipo)) {
            matriculaService.cancelarEmAbertoDoAluno(usuario.getId());
        }

        usuarioRepository.delete(usuario);
        sessaoService.encerrarSessao();
    }
}
