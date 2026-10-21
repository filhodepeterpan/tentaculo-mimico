package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.view.AlunoMatriculadoView;
import br.com.tentaculomimico.model.view.AlunoPendenteView;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.service.MatriculaService;
import br.com.tentaculomimico.service.PerfilService;
import br.com.tentaculomimico.service.SessaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class PerfilController {

    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final PerfilService perfilService;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;

    public PerfilController(UsuarioRepository usuarioRepository, CursoRepository cursoRepository,
                            PerfilService perfilService, MatriculaService matriculaService,
                            SessaoService sessaoService) {
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.perfilService = perfilService;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
    }

    @GetMapping("/perfil/aluno/{id}")
    public String perfilAluno(@PathVariable String id, Model model) {
        Usuario usuarioLogado = sessaoService.usuarioLogado();

        if (usuarioLogado == null) {
            return "redirect:/acesso-negado";
        }

        boolean ehPerfilProprio = usuarioLogado.getId().equals(id);
        boolean ehAdmin = "ADMINISTRADOR".equalsIgnoreCase(usuarioLogado.getTipoUsuario().name());

        if ("ALUNO".equalsIgnoreCase(usuarioLogado.getTipoUsuario().name()) && !ehPerfilProprio) {
            return "redirect:/acesso-negado";
        }

        Usuario aluno = usuarioRepository.findById(id).orElse(null);
        if (aluno == null) {
            return "redirect:/cursos";
        }

        model.addAttribute("aluno", aluno);
        model.addAttribute("ehPerfilProprio", ehPerfilProprio);
        model.addAttribute("podeEditar", ehPerfilProprio || ehAdmin);
        model.addAttribute("perfilId", id);
        model.addAttribute("matriculas", matriculaService.listarCursosDoAluno(id));
        model.addAttribute("dataNascimentoFormatada", formatarData(aluno));

        return "perfil-aluno";
    }

    @GetMapping("/perfil/professor/{id}")
    public String perfilProfessor(@PathVariable String id, Model model) {
        Usuario usuarioLogado = sessaoService.usuarioLogado();

        Usuario professor = usuarioRepository.findById(id).orElse(null);
        if (professor == null) {
            return "redirect:/cursos";
        }

        boolean ehPerfilProprio = usuarioLogado != null && usuarioLogado.getId().equals(id);
        boolean ehAdmin = usuarioLogado != null
                && "ADMINISTRADOR".equalsIgnoreCase(usuarioLogado.getTipoUsuario().name());
        boolean podeVerDadosSensiveis = ehPerfilProprio || ehAdmin;

        List<Curso> cursosDoProfessor = cursoRepository.findByProfessorId(id);

        model.addAttribute("podeEditar", ehPerfilProprio || ehAdmin);
        model.addAttribute("perfilId", id);
        model.addAttribute("professor", professor);
        model.addAttribute("ehPerfilProprio", ehPerfilProprio);
        model.addAttribute("podeVerDadosSensiveis", podeVerDadosSensiveis);
        model.addAttribute("cursosDoProfessor", cursosDoProfessor);
        model.addAttribute("dataNascimentoFormatada", formatarData(professor));

        if (ehPerfilProprio) {
            List<AlunoMatriculadoView> alunosMatriculados = matriculaService.listarAlunosMatriculados(id);
            List<AlunoPendenteView> alunosPendentes = matriculaService.listarAlunosPendentes(id);

            model.addAttribute("alunosMatriculados", alunosMatriculados);
            model.addAttribute("alunosPendentes", alunosPendentes);
            model.addAttribute("totalPendentes", alunosPendentes.size());
        }

        return "perfil-professor";
    }

    @GetMapping("/perfil/editar")
    public String editarPerfil(@RequestParam(value = "id", required = false) String id, Model model) {
        Usuario logado = sessaoService.usuarioLogado();
        if (logado == null) {
            return "redirect:/login";
        }
        Usuario alvo = alvoDaEdicao(logado, id);
        boolean editandoOutro = !alvo.getId().equals(logado.getId());
        if (editandoOutro && !"ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name())) {
            return "redirect:/acesso-negado";
        }

        preencherEdicao(model, alvo,
                alvo.getNome(),
                alvo.getEmail(),
                formatarCpf(alvo.getCpf()),
                alvo.getDataNascimento() != null ? alvo.getDataNascimento().toString() : "");
        return "perfil-editar";
    }

    @PostMapping("/perfil/editar")
    public String salvarEdicaoPerfil(
            @RequestParam(value = "id", required = false) String id,
            @RequestParam("nome") String nome,
            @RequestParam("email") String email,
            @RequestParam("cpf") String cpf,
            @RequestParam("data-nascimento") String dataNascimento,
            @RequestParam(value = "senha-atual", required = false) String senhaAtual,
            @RequestParam(value = "nova-senha", required = false) String novaSenha,
            @RequestParam(value = "confirmar-nova-senha", required = false) String confirmarNovaSenha,
            @RequestParam(value = "foto-perfil", required = false) MultipartFile fotoPerfil,
            @RequestParam(value = "voltar", required = false) String voltar,
            Model model
    ) {
        Usuario logado = sessaoService.usuarioLogado();
        if (logado == null) {
            return "redirect:/login";
        }
        Usuario alvo = alvoDaEdicao(logado, id);

        try {
            perfilService.atualizarPerfil(alvo.getId(), nome, email, cpf, dataNascimento,
                    senhaAtual, novaSenha, confirmarNovaSenha, fotoPerfil);

            if ("/admin-alunos".equals(voltar) || "/admin-professores".equals(voltar)) {
                return "redirect:" + voltar;
            }
            String tipo = "PROFESSOR".equalsIgnoreCase(alvo.getTipoUsuario().name()) ? "professor" : "aluno";
            return "redirect:/perfil/" + tipo + "/" + alvo.getId();
        } catch (PerfilService.DadosInvalidosException e) {
            Map<String, String> erros = e.getErros();
            model.addAttribute("erroNome", erros.get("nome"));
            model.addAttribute("erroEmail", erros.get("email"));
            model.addAttribute("erroCpf", erros.get("cpf"));
            model.addAttribute("erroDataNascimento", erros.get("dataNascimento"));
            model.addAttribute("erroSenhaAtual", erros.get("senhaAtual"));
            model.addAttribute("erroNovaSenha", erros.get("novaSenha"));
            model.addAttribute("erroConfirmarNovaSenha", erros.get("confirmarNovaSenha"));
            preencherEdicao(model, alvo, nome, email, cpf, dataNascimento);
            return "perfil-editar";
        } catch (RuntimeException e) {
            model.addAttribute("erroGeral", e.getMessage());
            preencherEdicao(model, alvo, nome, email, cpf, dataNascimento);
            return "perfil-editar";
        }
    }

    private Usuario alvoDaEdicao(Usuario logado, String id) {
        if (id == null || id.isBlank() || id.equals(logado.getId())) {
            return logado;
        }
        return usuarioRepository.findById(id).orElse(logado);
    }

    @PostMapping("/perfil-excluir")
    public String excluirConta(RedirectAttributes redirectAttributes) {
        Usuario logado = sessaoService.usuarioLogado();
        if (logado == null) {
            return "redirect:/login";
        }

        if ("ADMINISTRADOR".equalsIgnoreCase(logado.getTipoUsuario().name())) {
            return "redirect:/acesso-negado";
        }

        try {
            perfilService.excluirContaDoUsuarioLogado();
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("erroExclusaoConta", e.getMessage());
            String tipo = "PROFESSOR".equalsIgnoreCase(logado.getTipoUsuario().name()) ? "professor" : "aluno";
            return "redirect:/perfil/" + tipo + "/" + logado.getId();
        }
    }

    private void preencherEdicao(Model model, Usuario usuario, String nome, String email,
                                 String cpf, String dataNascimento) {
        boolean contaLocal = usuario.getAutenticacao() == null
                || usuario.getAutenticacao().getProvedor() == Provedor.LOCAL;

        model.addAttribute("usuario", usuario);
        model.addAttribute("contaLocal", contaLocal);
        model.addAttribute("nomePreenchido", nome);
        model.addAttribute("emailPreenchido", email);
        model.addAttribute("cpfPreenchido", cpf);
        model.addAttribute("dataNascimentoPreenchida", dataNascimento);
    }

    private String formatarCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11) {
            return cpf;
        }
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private String formatarData(Usuario usuario) {
        if (usuario.getDataNascimento() == null) {
            return "";
        }
        return usuario.getDataNascimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}