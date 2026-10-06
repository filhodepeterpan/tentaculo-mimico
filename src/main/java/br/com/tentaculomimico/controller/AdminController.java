package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.dto.ResumoAdminResposta;
import br.com.tentaculomimico.model.AlunoAdminResposta;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.ProfessorAdminResposta;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.service.AdminService;
import br.com.tentaculomimico.service.SessaoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService servicoAdmin;
    private final SessaoService sessaoService;

    public AdminController(AdminService servicoAdmin, SessaoService sessaoService) {
        this.servicoAdmin = servicoAdmin;
        this.sessaoService = sessaoService;
    }

    @GetMapping("/resumo")
    public ResumoAdminResposta resumoDoPainel() {
        exigirAdministrador();
        return servicoAdmin.resumo();
    }

    @GetMapping("/alunos")
    public List<AlunoAdminResposta> listaDeAlunos() {
        exigirAdministrador();
        return servicoAdmin.listarAlunos();
    }


    @GetMapping("/professores")
    public List<ProfessorAdminResposta> listaDeProfessores() {
        exigirAdministrador();
        return servicoAdmin.listarProfessores();
    }



    @GetMapping("/cursos")
    public List<Curso> listaDeCursos() {
        exigirAdministrador();
        return servicoAdmin.listarCursos();
    }

    private void exigirAdministrador() {
        Usuario usuario = sessaoService.usuarioLogado();
        if (usuario == null || usuario.getTipoUsuario() != TipoUsuario.ADMINISTRADOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso restrito a administradores.");
        }
    }
}