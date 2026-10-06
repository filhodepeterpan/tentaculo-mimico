package br.com.tentaculomimico.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.SessaoService;

// Fica separado do HomeController (que já cuida só da "/") pra não
// dar conflito quando o time for juntar as branches.
@Controller
public class PaginasController {

    private final SessaoService sessaoService;

    public PaginasController(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "sobre";
    }

    @GetMapping("/cadastro")
    public String cadastro() {
        return "cadastro";
    }

    @GetMapping("/admin")
    public String painelAdmin() {
        return paginaDeAdmin("painel-admin");
    }

    @GetMapping("/admin-alunos")
    public String adminAlunos() {
        return paginaDeAdmin("admin-alunos");
    }

    @GetMapping("/admin-professores")
    public String adminProfessores() {
        return paginaDeAdmin("admin-professores");
    }

    @GetMapping("/admin-cursos")
    public String adminCursos() {
        return paginaDeAdmin("admin-cursos");
    }

    // Devolve a página só se quem pediu é administrador.
    private String paginaDeAdmin(String template) {
        Usuario logado = sessaoService.usuarioLogado();

        if (logado == null) {
            return "redirect:/login";
        }
        if (!"ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name())) {
            return "redirect:/acesso-negado";
        }
        return template;
    }

    @GetMapping("/contato")
    public String contato() {
        return "contato";
    }

    @GetMapping("/doacoes")
    public String doacoes() {
        return "doacoes";
    }

    @GetMapping("/login")
    public String login() {

        return "login";
    }

    @GetMapping("/componentes")
    public String componentes() { return "exemplos-componentes"; }

    // O back ainda precisa: ler o token da query string, validar contra o
    // banco (existe? não expirou? não foi usado?) e então colocar no model
    // emailConfirmado=true/false e, se false, mensagemErro com o motivo.
    // Sem isso, a página sempre cai no estado de erro (fallback seguro).
    @GetMapping("/confirmar-email")
    public String confirmarEmail() {
        return "confirmar-email";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }
}