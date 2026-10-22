package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.view.SemanaCalendarioView;
import br.com.tentaculomimico.service.CalendarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Alimenta o componente de calendário (fragments/calendario), que aparece na
// landing e em /cursos. É público: só expõe dados que já aparecem em /cursos.
@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

    private final CalendarioService calendarioService;

    public CalendarioController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping("/semana")
    public SemanaCalendarioView semana() {
        return calendarioService.semanaAtual();
    }
}
