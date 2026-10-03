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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// URLs deste controller:
//   GET  /perfil/aluno/{id}       GET  /perfil/professor/{id}
//   GET  /perfil/editar           POST /perfil/editar
//   POST /perfil-excluir
//
// PREMISSAS assumidas neste controller (o back deve se adaptar a elas):
//   - UsuarioRepository já existe (mesmo padrão do CursoRepository).
//   - SessaoService.usuarioLogado() devolve o Usuario autenticado, ou null
//     se ninguém estiver logado — é o back quem decide como isso é resolvido
//     (Spring Security, sessão própria, etc.), aqui só se consome o método.
//   - Usuario.getTipoUsuario() é um enum cujo .name() vem em maiúsculas
//     (ALUNO/PROFESSOR/ADMINISTRADOR); convertido pra minúsculo ao passar
//     pro model, porque é assim que os templates comparam.
//   - CursoRepository precisa do método findByProfessorId(String).
//   - MatriculaService precisa de: listarCursosDoAluno(alunoId),
//     listarAlunosMatriculados(professorId), listarAlunosPendentes(professorId).
//   - PerfilService precisa de: atualizarPerfil(...) e
//     excluirContaDoUsuarioLogado() — este último deve respeitar RN011/012/019.
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

        // RN confirmada com o PO: aluno não pode visualizar perfil de outro
        // aluno — bloqueia mesmo não havendo, hoje, nenhum link que leve a
        // essa URL diretamente.
        if (usuarioLogado != null
                && "ALUNO".equalsIgnoreCase(usuarioLogado.getTipoUsuario().name())
                && !usuarioLogado.getId().equals(id)) {
            return "redirect:/acesso-negado";
        }

        Usuario aluno = usuarioRepository.findById(id).orElse(null);
        if (aluno == null) {
            return "redirect:/cursos";
        }

        boolean ehPerfilProprio = usuarioLogado != null && usuarioLogado.getId().equals(id);

        model.addAttribute("aluno", aluno);
        model.addAttribute("ehPerfilProprio", ehPerfilProprio);
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

        model.addAttribute("professor", professor);
        model.addAttribute("ehPerfilProprio", ehPerfilProprio);
        model.addAttribute("podeVerDadosSensiveis", podeVerDadosSensiveis);
        model.addAttribute("cursosDoProfessor", cursosDoProfessor);
        model.addAttribute("dataNascimentoFormatada", formatarData(professor));

        // Alunos matriculados e pendentes só importam pro próprio professor
        // ver (dado sensível de terceiros) — nem busca se não for o dono.
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
    public String editarPerfil(Model model) {
        Usuario usuario = sessaoService.usuarioLogado();
        if (usuario == null) {
            return "redirect:/login";
        }

        preencherEdicao(model, usuario,
                usuario.getNome(),
                usuario.getEmail(),
                formatarCpf(usuario.getCpf()),
                usuario.getDataNascimento() != null ? usuario.getDataNascimento().toString() : "");
        return "perfil-editar";
    }

    @PostMapping("/perfil/editar")
    public String salvarEdicaoPerfil(
            @RequestParam("nome") String nome,
            @RequestParam("email") String email,
            @RequestParam("cpf") String cpf,
            @RequestParam("data-nascimento") String dataNascimento,
            @RequestParam(value = "senha-atual", required = false) String senhaAtual,
            @RequestParam(value = "nova-senha", required = false) String novaSenha,
            @RequestParam(value = "confirmar-nova-senha", required = false) String confirmarNovaSenha,
            @RequestParam(value = "foto-perfil", required = false) MultipartFile fotoPerfil,
            Model model
    ) {
        Usuario logado = sessaoService.usuarioLogado();
        if (logado == null) {
            return "redirect:/login";
        }

        try {
            perfilService.atualizarPerfil(nome, email, cpf, dataNascimento,
                    senhaAtual, novaSenha, confirmarNovaSenha, fotoPerfil);

            String tipo = "PROFESSOR".equalsIgnoreCase(logado.getTipoUsuario().name()) ? "professor" : "aluno";
            return "redirect:/perfil/" + tipo + "/" + logado.getId();
        } catch (PerfilService.DadosInvalidosException e) {
            Map<String, String> erros = e.getErros();
            model.addAttribute("erroNome", erros.get("nome"));
            model.addAttribute("erroEmail", erros.get("email"));
            model.addAttribute("erroCpf", erros.get("cpf"));
            model.addAttribute("erroDataNascimento", erros.get("dataNascimento"));
            model.addAttribute("erroSenhaAtual", erros.get("senhaAtual"));
            model.addAttribute("erroNovaSenha", erros.get("novaSenha"));
            model.addAttribute("erroConfirmarNovaSenha", erros.get("confirmarNovaSenha"));
            preencherEdicao(model, logado, nome, email, cpf, dataNascimento);
            return "perfil-editar";
        } catch (RuntimeException e) {
            model.addAttribute("erroGeral", e.getMessage());
            preencherEdicao(model, logado, nome, email, cpf, dataNascimento);
            return "perfil-editar";
        }
    }

    @PostMapping("/perfil-excluir")
    public String excluirConta() {
        perfilService.excluirContaDoUsuarioLogado();
        return "redirect:/login";
    }

    // Campos do formulário de edição: no GET vêm do usuário salvo; no POST com
    // erro voltam com o que a pessoa digitou. "contaLocal" esconde a seção de
    // senha pra quem entra com Google (não tem senha local).
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

    // O CPF é guardado só com dígitos; na tela aparece como 000.000.000-00.
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
