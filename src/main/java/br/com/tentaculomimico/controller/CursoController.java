package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.service.CursoService;
import br.com.tentaculomimico.service.MatriculaService;
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

@Controller
public class CursoController {

    private final CursoService cursoService;
    private final CursoRepository cursoRepository;
    private final MatriculaService matriculaService;
    private final Cloudinary cloudinary;

    public CursoController(CursoService cursoService, CursoRepository cursoRepository,
                           MatriculaService matriculaService, Cloudinary cloudinary) {
        this.cursoService = cursoService;
        this.cursoRepository = cursoRepository;
        this.matriculaService = matriculaService;
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
        model.addAttribute("curso", curso);
        return "curso-detalhes";
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
