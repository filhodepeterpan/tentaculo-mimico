package br.com.tentaculomimico.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.SessaoService;
import br.com.tentaculomimico.service.AdminService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

// Fica separado do HomeController (que já cuida só da "/") pra não
// dar conflito quando o time for juntar as branches.
@Controller
public class PaginasController {

    private final SessaoService sessaoService;
    private final AdminService adminService;

    public PaginasController(SessaoService sessaoService, AdminService adminService) {
        this.sessaoService = sessaoService;
        this.adminService = adminService;
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
    public String adminAlunos(Model model) {
        String resultado = paginaDeAdmin("admin-alunos");
        if (resultado.equals("admin-alunos")){
            model.addAttribute("listaDeAlunos", adminService.listarAlunos());
        }

        return resultado;
    }

    @GetMapping("/admin-professores")
    public String adminProfessores(Model model) {
        String resultado = paginaDeAdmin("admin-professores");
        if (resultado.equals("admin-professores")) {
            model.addAttribute("listaDeProfessores", adminService.listarProfessores());
        }

        return resultado;
    }



    @GetMapping("/admin-cursos")
    public String adminCursos(Model model) {

        String resultado = paginaDeAdmin("admin-cursos");

        if (resultado.equals("admin-cursos")) {
            model.addAttribute("listaDeCursos", adminService.listarCursosComAlunos());
        } return resultado;
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
    public String doacoes(@RequestParam(required = false) String retorno, Model model) {
        model.addAttribute("mostrarAgradecimentoDoacao", "pagbank".equals(retorno));
        return "doacoes";
    }

    @GetMapping("/login")
    public String login() {
        if (sessaoService.usuarioLogado() != null) {
            return "redirect:cursos";
        }

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
