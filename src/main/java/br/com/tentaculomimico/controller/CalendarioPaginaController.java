package br.com.tentaculomimico.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Página do calendário da semana (templates/calendario.html). Os dados em si vêm
// de CalendarioController (GET /api/calendario/semana), buscados pelo JavaScript.
@Controller
public class CalendarioPaginaController {

    @GetMapping("/calendario")
    public String calendario() {
        return "calendario";
    }
}
