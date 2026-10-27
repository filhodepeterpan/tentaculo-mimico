package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.CursoService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.SessaoService;
import br.com.tentaculomimico.service.AdminService;
import br.com.tentaculomimico.service.PerfilService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Fica separado do HomeController (que já cuida só da "/") pra não
// dar conflito quando o time for juntar as branches.
@Controller
public class PaginasController {

    private final SessaoService sessaoService;
    private final AdminService adminService;
    private final PerfilService perfilService;

    private final CursoService cursoService;

    public PaginasController(SessaoService sessaoService, AdminService adminService,
                             PerfilService perfilService, CursoService cursoService) {
        this.sessaoService = sessaoService;
        this.adminService = adminService;
        this.perfilService = perfilService;
        this.cursoService = cursoService;
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
        }
        return resultado;
    }

    @PostMapping("/admin-alunos/{id}/remover")
    public String removerAluno(@PathVariable String id) {
        String resultado = paginaDeAdmin("admin-alunos");
        if (!resultado.equals("admin-alunos")) {
            return resultado;
        }
        adminService.removerAluno(id);
        return "redirect:/admin-alunos";
    }

    /**
     * Endpoint para o Administrador excluir um usuário (ex: Professor) pelo painel.
     */
    @PostMapping("/admin/usuarios/{id}/excluir")
    public String excluirUsuario(@PathVariable String id, RedirectAttributes ra) {
        Usuario logado = sessaoService.usuarioLogado();
        if (logado == null) {
            return "redirect:/login";
        }
        if (!"ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name())) {
            return "redirect:/acesso-negado";
        }
        try {
            perfilService.excluirUsuarioComoAdmin(id);
            ra.addFlashAttribute("sucesso", "Usuário excluído com sucesso.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("erroExclusao", e.getMessage());
        }
        return "redirect:/admin-professores";
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


    @PostMapping("/admin-cursos/{id}/remover")
    public String removerCurso(@PathVariable String id, RedirectAttributes ra) {
        String resultado = paginaDeAdmin("admin-cursos");
        if (!resultado.equals("admin-cursos")) {
            return resultado;
        }
        try {
            cursoService.excluirCurso(id);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("erroExclusao", e.getMessage());
        }
        return "redirect:/admin-cursos";
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
    public String login(@RequestParam(required = false) String verifique, Model model) {
        if (sessaoService.usuarioLogado() != null) {
            return "redirect:cursos";
        }

        // Vem do redirect do POST /cadastro. Sem sessão/flash, então funciona igual no Render.
        if ("email".equals(verifique)) {
            model.addAttribute("mostrarModalEmailEnviado", true);
        } else if ("falha".equals(verifique)) {
            model.addAttribute("erroGeral",
                    "Sua conta foi criada, mas não conseguimos enviar o e-mail de confirmação agora. "
                    + "Entre com seu e-mail e senha em alguns minutos para receber um novo link.");
        }

        return "login";
    }

    @GetMapping("/componentes")
    public String componentes() {
        return "exemplos-componentes";
    }


    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }
}