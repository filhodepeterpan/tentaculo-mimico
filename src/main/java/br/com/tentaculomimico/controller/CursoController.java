package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Horario;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.service.CursoService;
import br.com.tentaculomimico.service.MatriculaService;
import br.com.tentaculomimico.service.SessaoService;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// Mesmas premissas assumidas do PerfilController: SessaoService existe e
// devolve o Usuario logado (ou null); Usuario.getTipoUsuario().name() vem
// em maiúsculas. MatriculaService precisa de:
// buscarStatusDoAluno(alunoId, cursoId) devolve a MatriculaCursoView da
// matrícula do aluno logado nesse curso, ou null se nunca solicitou.
@Controller
public class CursoController {

    private final CursoService cursoService;
    private final CursoRepository cursoRepository;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;
    private final Cloudinary cloudinary;
    private final UsuarioRepository usuarioRepository;

    public CursoController(CursoService cursoService, CursoRepository cursoRepository,
                           MatriculaService matriculaService, SessaoService sessaoService,
                           Cloudinary cloudinary, UsuarioRepository usuarioRepository) {
        this.cursoService = cursoService;
        this.cursoRepository = cursoRepository;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
        this.cloudinary = cloudinary;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/cursos/novo")
    public String novoCurso() {
        return "cadastro-curso";
    }

    @GetMapping("/cursos")
    public String listarCursos(Model model) {
        model.addAttribute("cursos", cursoRepository.findAll());
        return "cursos";
    }

    @PostMapping("/cursos")
    public String salvarCurso(
            @RequestParam("nome") String nome,
            @RequestParam("descricao") String descricao,
            @RequestParam("carga-horaria") String cargaHoraria,
            @RequestParam("preco") String preco,
            @RequestParam("vagas-totais") String vagasTotais,
            @RequestParam(value = "diasSemana", required = false) List<String> diasSemana,
            @RequestParam("hora-inicio") String horaInicio,
            @RequestParam("hora-fim") String horaFim,
            @RequestParam("data-inicio") String dataInicio,
            @RequestParam(value = "data-fim", required = false) String dataFim,
            @RequestParam(value = "imagem-capa", required = false) MultipartFile imagemCapa,
            Model model
    ) {
        try {
            String urlImagem = enviarImagem(imagemCapa, "cursos");

            cursoService.cadastrarCurso(nome, descricao, cargaHoraria, preco, vagasTotais,
                    diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);
            return "redirect:/cursos";
        } catch (RuntimeException | IOException e) {
            model.addAttribute("erroGeral", e.getMessage());
            return "cadastro-curso";
        }
    }

    @GetMapping("/cursos/{id}")
    public String detalhesCurso(@PathVariable String id, Model model) {
        Curso curso = cursoRepository.findById(id).orElse(null);
        if (curso == null) {
            return "redirect:/cursos";
        }

        // Busca o professor criador do curso no banco de dados
        if (curso.getProfessorId() != null) {
            usuarioRepository.findById(curso.getProfessorId()).ifPresent(professor -> {
                model.addAttribute("professor", professor);
                // Atualiza em memória o nome/foto para garantir exibição atualizada
                curso.setProfessorNome(professor.getNome());
                curso.setProfessorFotoPerfil(professor.getFotoPerfil());
            });
        }

        Usuario usuarioLogado = sessaoService.usuarioLogado();
        String tipoUsuarioLogado = usuarioLogado != null
                ? usuarioLogado.getTipoUsuario().name().toLowerCase()
                : null;
        boolean ehProprietario = usuarioLogado != null
                && "professor".equals(tipoUsuarioLogado)
                && usuarioLogado.getId().equals(curso.getProfessorId());

        model.addAttribute("curso", curso);
        model.addAttribute("tipoUsuarioLogado", tipoUsuarioLogado);
        model.addAttribute("ehProprietario", ehProprietario);

        if ("aluno".equals(tipoUsuarioLogado)) {
            var statusView = matriculaService.buscarStatusDoAluno(usuarioLogado.getId(), id);
            model.addAttribute("statusMatricula", statusView != null ? statusView.getStatus() : null);
            model.addAttribute("matriculaId", statusView != null ? statusView.getId() : null);
        }

        return "curso-detalhes";
    }

    // Reaproveita o template de cadastro, pré-preenchido. A presença do atributo
    // "curso" no model é o que faz cadastro-curso.html entrar em modo edição
    // (título, botão e action apontando pra POST /cursos/{id}/editar).
    @GetMapping("/cursos/{id}/editar")
    public String editarCurso(@PathVariable String id, Model model) {
        if (sessaoService.usuarioLogado() == null) {
            return "redirect:/login";
        }

        Curso curso;
        try {
            curso = cursoService.buscarParaEdicao(id); // só o dono (ou admin) passa
        } catch (RuntimeException e) {
            return "redirect:/cursos/" + id;
        }

        model.addAttribute("curso", curso);
        preencherFormulario(model, curso);
        return "cadastro-curso";
    }

    @PostMapping("/cursos/{id}/editar")
    public String atualizarCurso(
            @PathVariable String id,
            @RequestParam("nome") String nome,
            @RequestParam("descricao") String descricao,
            @RequestParam("carga-horaria") String cargaHoraria,
            @RequestParam("preco") String preco,
            @RequestParam("vagas-totais") String vagasTotais,
            @RequestParam(value = "diasSemana", required = false) List<String> diasSemana,
            @RequestParam("hora-inicio") String horaInicio,
            @RequestParam("hora-fim") String horaFim,
            @RequestParam("data-inicio") String dataInicio,
            @RequestParam(value = "data-fim", required = false) String dataFim,
            @RequestParam(value = "imagem-capa", required = false) MultipartFile imagemCapa,
            Model model
    ) {
        if (sessaoService.usuarioLogado() == null) {
            return "redirect:/login";
        }

        Curso curso;
        try {
            curso = cursoService.buscarParaEdicao(id);
        } catch (RuntimeException e) {
            return "redirect:/cursos/" + id;
        }

        try {
            // null = nenhuma imagem nova enviada: o service mantém a atual.
            String urlImagem = enviarImagem(imagemCapa, "cursos");

            cursoService.atualizarCurso(id, nome, descricao, cargaHoraria, preco, vagasTotais,
                    diasSemana, horaInicio, horaFim, dataInicio, dataFim, urlImagem);
            return "redirect:/cursos/" + id;
        } catch (RuntimeException | IOException e) {
            // Devolve o formulário com o que a pessoa digitou, não com o valor salvo.
            model.addAttribute("curso", curso);
            model.addAttribute("nomePreenchido", nome);
            model.addAttribute("descricaoPreenchida", descricao);
            model.addAttribute("cargaHorariaPreenchida", cargaHoraria);
            model.addAttribute("precoPreenchido", preco);
            model.addAttribute("vagasTotaisPreenchidas", vagasTotais);
            model.addAttribute("diasSemanaSelecionados", diasSemana);
            model.addAttribute("horaInicioPreenchida", horaInicio);
            model.addAttribute("horaFimPreenchida", horaFim);
            model.addAttribute("dataInicioPreenchida", dataInicio);
            model.addAttribute("dataFimPreenchida", dataFim);
            model.addAttribute("erroGeral", e.getMessage());
            return "cadastro-curso";
        }
    }

    @PostMapping("/cursos/{id}/excluir")
    public String excluirCurso(@PathVariable String id, Model model) {
        try {
            cursoService.excluirCurso(id); // deve respeitar RN025 (não excluir com aluno matriculado)
            return "redirect:/perfil/professor/" + sessaoService.usuarioLogado().getId();
        } catch (RuntimeException e) {
            model.addAttribute("curso", cursoRepository.findById(id).orElse(null));
            model.addAttribute("erroGeral", e.getMessage());
            return "curso-detalhes";
        }
    }

    @PostMapping("/cursos/{id}/matricula")
    public String matricularCurso(@PathVariable String id, Model model) {
        try {
            matriculaService.solicitarMatricula(id);
            return "redirect:/cursos/" + id;
        } catch (RuntimeException e) {
            Curso curso = cursoRepository.findById(id).orElse(null);
            model.addAttribute("curso", curso);
            model.addAttribute("erroMatricula", e.getMessage());
            return "curso-detalhes";
        }
    }

    // Preenche os campos do formulário a partir de um curso salvo. Os nomes
    // (*Preenchida/*Preenchido, diasSemanaSelecionados) são os que
    // cadastro-curso.html lê. Tudo vai como String, no formato dos inputs HTML
    // (data = yyyy-MM-dd, hora = HH:mm).
    private void preencherFormulario(Model model, Curso curso) {
        model.addAttribute("nomePreenchido", curso.getNome());
        model.addAttribute("descricaoPreenchida", curso.getDescricao());
        model.addAttribute("cargaHorariaPreenchida", String.valueOf(curso.getCargaHoraria()));
        model.addAttribute("precoPreenchido",
                curso.getPreco() != null ? curso.getPreco().toPlainString() : "");
        model.addAttribute("vagasTotaisPreenchidas", String.valueOf(curso.getVagasTotais()));
        model.addAttribute("dataInicioPreenchida",
                curso.getDataInicio() != null ? curso.getDataInicio().toString() : "");
        model.addAttribute("dataFimPreenchida",
                curso.getDataFim() != null ? curso.getDataFim().toString() : "");

        // O formulário tem um único horário (início/fim) aplicado a todos os
        // dias marcados, então basta ler o primeiro bloco.
        List<Horario> horarios = curso.getHorarios();
        if (horarios != null && !horarios.isEmpty()) {
            DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
            model.addAttribute("horaInicioPreenchida", horarios.get(0).getInicio().format(hhmm));
            model.addAttribute("horaFimPreenchida", horarios.get(0).getFim().format(hhmm));
            model.addAttribute("diasSemanaSelecionados",
                    horarios.stream().map(h -> h.getDiaSemana().name()).distinct().toList());
        }
    }

    // Sobe o arquivo pro Cloudinary, numa pasta separada por tipo
    // (tentaculo-mimico/cursos, tentaculo-mimico/perfis, etc.) e devolve a
    // URL segura (https) já pronta pra salvar no banco. Reaproveitável pra
    // foto de perfil também — é só chamar enviarImagem(arquivo, "perfis").
    private String enviarImagem(MultipartFile arquivo, String pasta) throws IOException {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }

        String tipo = arquivo.getContentType();
        if (tipo == null || !tipo.startsWith("image/")) {
            throw new RuntimeException("O arquivo enviado não é uma imagem válida.");
        }

        Map<String, Object> opcoes = ObjectUtils.asMap(
                "folder", "tentaculo-mimico/" + pasta,
                "resource_type", "image"
        );

        Map resultado = cloudinary.uploader().upload(arquivo.getBytes(), opcoes);
        return (String) resultado.get("secure_url");
    }
}