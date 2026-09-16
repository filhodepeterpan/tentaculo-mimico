package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class CursoController {

    @GetMapping("/cursos")
    public String listarCursos(Model model) {
        model.addAttribute("cursos", cursosDeExemplo());
        return "cursos";
    }

    @GetMapping("/cursos/novo")
    public String novoCurso() {
        return "cadastro-curso";
    }

    @GetMapping("/cursos/{id}")
    public String detalhesCurso(@PathVariable String id, Model model) {
        Curso curso = cursosDeExemplo().stream()
            .filter(c -> c.getId().equals(id))
            .findFirst()
            .orElse(null);

        if (curso == null) {
            return "redirect:/cursos";
        }

        model.addAttribute("curso", curso);
        return "curso-detalhes";
    }

    // Dado fake só pra visualizar/iterar no HTML — troca por dado real do
    // MongoDB quando o back conectar o repositório de verdade.
    private List<Curso> cursosDeExemplo() {
        return List.of(
            new Curso("1", "Oficina de Escrita de Peças Teatrais", 12,
                "Aprenda a estruturar cenas, diálogos e conflitos dramáticos.", 249.90, 20, 14,
                "Marina Alves", "Seg e Qua · 19h", null),
            new Curso("2", "Introdução à História da Arte", 20,
                "Um panorama dos grandes movimentos artísticos, da Renascença ao Modernismo.", 199.00, 30, 30,
                "Eduardo Ramos", "Ter e Qui · 18h", null),
            new Curso("3", "Violão Popular para Iniciantes", 16,
                "Acordes, ritmos e repertório popular brasileiro do zero.", 179.90, 25, 3,
                "Carlos Bittencourt", "Sáb · 10h", null),
            new Curso("4", "Produção Musical no Computador", 30,
                "Grave, edite e produza suas próprias faixas usando ferramentas digitais.", 349.00, 15, 0,
                "Fernanda Lopes", "Qua e Sex · 20h", null),
            new Curso("5", "Dança Contemporânea: Corpo e Movimento", 24,
                "Explore técnicas de improvisação e composição coreográfica.", 229.90, 18, 9,
                "Juliana Costa", "Seg, Qua e Sex · 17h", null),
            new Curso("6", "Cerâmica e Modelagem em Argila", 18,
                "Técnicas manuais de modelagem, esmaltação e queima.", 259.00, 12, 5,
                "Ricardo Nunes", "Sáb · 14h", null),
            new Curso("7", "Fotografia Analógica e Revelação", 14,
                "Da captura em filme à revelação em laboratório caseiro.", 289.90, 16, 16,
                "Beatriz Martins", "Dom · 9h", null),
            new Curso("8", "Canto Coral: Técnica Vocal em Grupo", 20,
                "Desenvolva afinação, respiração e projeção vocal cantando em conjunto.", 149.90, 40, 22,
                "Paulo Henrique", "Ter e Qui · 19h30", null),
            new Curso("9", "Cinema Brasileiro: da Retomada aos Dias Atuais", 10,
                "Um estudo crítico das principais obras do cinema nacional recente.", 169.00, 35, 1,
                "Camila Dias", "Qui · 20h", null),
            new Curso("10", "Cultura Popular Nordestina", 16,
                "Maracatu, cordel, xilogravura e outras expressões da cultura popular.", 189.90, 22, 22,
                "José Ferreira", "Sáb · 16h", null)
        );
    }
}
