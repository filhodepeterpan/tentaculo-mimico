package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

    /* dani e millena,a botei uma lista genérica de cursos só pra testar a página (não tá com dados do banco) */
    private List<Curso> cursosDeExemplo() {
        return List.of(
            new Curso("1", "Oficina de Escrita de Peças Teatrais", 12,
                "Aprenda a estruturar cenas, diálogos e conflitos dramáticos.", 249.90, 20, 14),
            new Curso("2", "Introdução à História da Arte", 20,
                "Um panorama dos grandes movimentos artísticos, da Renascença ao Modernismo.", 199.00, 30, 30),
            new Curso("3", "Violão Popular para Iniciantes", 16,
                "Acordes, ritmos e repertório popular brasileiro do zero.", 179.90, 25, 3),
            new Curso("4", "Produção Musical no Computador", 30,
                "Grave, edite e produza suas próprias faixas usando ferramentas digitais.", 349.00, 15, 0),
            new Curso("5", "Dança Contemporânea: Corpo e Movimento", 24,
                "Explore técnicas de improvisação e composição coreográfica.", 229.90, 18, 9),
            new Curso("6", "Cerâmica e Modelagem em Argila", 18,
                "Técnicas manuais de modelagem, esmaltação e queima.", 259.00, 12, 5),
            new Curso("7", "Fotografia Analógica e Revelação", 14,
                "Da captura em filme à revelação em laboratório caseiro.", 289.90, 16, 16),
            new Curso("8", "Canto Coral: Técnica Vocal em Grupo", 20,
                "Desenvolva afinação, respiração e projeção vocal cantando em conjunto.", 149.90, 40, 22),
            new Curso("9", "Cinema Brasileiro: da Retomada aos Dias Atuais", 10,
                "Um estudo crítico das principais obras do cinema nacional recente.", 169.00, 35, 1),
            new Curso("10", "Cultura Popular Nordestina", 16,
                "Maracatu, cordel, xilogravura e outras expressões da cultura popular.", 189.90, 22, 22)
        );
    }
}
