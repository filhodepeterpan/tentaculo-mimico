package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.CursoRepository;
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
import java.util.List;
import java.util.Map;

// Mesmas premissas assumidas do PerfilController: SessaoService existe e
// devolve o Usuario logado (ou null); Usuario.getTipoUsuario().name() vem
// em maiúsculas. MatriculaService precisa de:
// buscarStatusDoAluno(alunoId, cursoId) — devolve a MatriculaCursoView da
// matrícula do aluno logado nesse curso, ou null se nunca solicitou.
@Controller
public class CursoController {

    private final CursoService cursoService;
    private final CursoRepository cursoRepository;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;
    private final Cloudinary cloudinary;

    public CursoController(CursoService cursoService, CursoRepository cursoRepository,
                           MatriculaService matriculaService, SessaoService sessaoService,
                           Cloudinary cloudinary) {
        this.cursoService = cursoService;
        this.cursoRepository = cursoRepository;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
        this.cloudinary = cloudinary;
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

        // Só interessa status/matriculaId quando é aluno olhando o curso —
        // é o que decide entre "Matricule-se", badge de status, "Pagar" ou
        // "Desistir" em curso-detalhes.html.
        if ("aluno".equals(tipoUsuarioLogado)) {
            var statusView = matriculaService.buscarStatusDoAluno(usuarioLogado.getId(), id);
            model.addAttribute("statusMatricula", statusView != null ? statusView.getStatus() : null);
            model.addAttribute("matriculaId", statusView != null ? statusView.getId() : null);
        }

        return "curso-detalhes";
    }

    @GetMapping("/cursos/{id}/editar")
    public String editarCurso(@PathVariable String id, Model model) {
        Curso curso = cursoRepository.findById(id).orElse(null);
        if (curso == null) {
            return "redirect:/cursos";
        }
        // Reaproveita o mesmo template de cadastro, só pré-preenchido —
        // cadastro-curso.html já lê *Preenchida/*Preenchido do model.
        model.addAttribute("curso", curso);
        model.addAttribute("nomePreenchido", curso.getNome());
        model.addAttribute("descricaoPreenchida", curso.getDescricao());
        model.addAttribute("cargaHorariaPreenchida", curso.getCargaHoraria());
        model.addAttribute("precoPreenchido", curso.getPreco());
        model.addAttribute("vagasTotaisPreenchidas", curso.getVagasTotais());
        model.addAttribute("dataInicioPreenchida", curso.getDataInicio());
        model.addAttribute("dataFimPreenchida", curso.getDataFim());
        // FALTA: pré-preencher horaInicioPreenchida/horaFimPreenchida/
        // diasSemanaSelecionados a partir de curso.getHorarios() — depende
        // de como Horario é modelado (não tenho essa classe), então deixei
        // de fora por ora. Sem isso, o formulário de edição abre com os
        // dias/horário em branco mesmo quando o curso já tem um definido.
        return "cadastro-curso";
    }

    @PostMapping("/cursos/{id}/excluir")
    public String excluirCurso(@PathVariable String id, Model model) {
        try {
            cursoService.excluirCurso(id); // deve respeitar RN025 (não excluir com aluno matriculado)
            return "redirect:/perfil-professor/" + sessaoService.usuarioLogado().getId();
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