package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.SessaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// Página do calendário da semana (templates/calendario.html). Os dados em si vêm
// de CalendarioController (GET /api/calendario/semana), buscados pelo JavaScript.
@Controller
public class CalendarioPaginaController {

    private final SessaoService sessaoService;

    public CalendarioPaginaController(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @GetMapping("/calendario")
    public String calendario(Model model) {
        // Só o professor pode escolher um horário no calendário para cadastrar um curso
        // (o template repassa isso ao JavaScript em data-professor).
        Usuario logado = sessaoService.usuarioLogado();
        boolean ehProfessor = logado != null
                && "PROFESSOR".equalsIgnoreCase(logado.getTipoUsuario().name());
        model.addAttribute("ehProfessor", ehProfessor);
        return "calendario";
    }
}
