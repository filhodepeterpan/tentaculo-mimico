package br.com.tentaculomimico.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Fica separado do HomeController (que já cuida só da "/") pra não
// dar conflito quando o time for juntar as branches.
@Controller
public class PaginasController {

    @GetMapping("/cursos")
    public String cursos() {
        return "cursos";
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "sobre";
    }

    @GetMapping("/cadastro")
    public String cadastro() {
        return "cadastro";
    }

    @GetMapping("/contato")
    public String contato() {
        return "contato";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/componentes")
    public String componentes() { return "exemplos-componentes"; }

    @GetMapping("/cadastro-curso")
    public String cadastroCursos() { return "cadastro-curso"; }
}
