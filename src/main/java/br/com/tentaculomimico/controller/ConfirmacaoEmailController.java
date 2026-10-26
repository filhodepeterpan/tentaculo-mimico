package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.AuthService.AutenticacaoException;
import br.com.tentaculomimico.service.VerificacaoEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class ConfirmacaoEmailController {

    private final VerificacaoEmailService verificacaoEmailService;

    @GetMapping("/confirmar-email")
    public String confirmarEmail(@RequestParam(required = false) String token, Model model) {
        try {
            verificacaoEmailService.confirmar(token);
            model.addAttribute("emailConfirmado", true);
        } catch (AutenticacaoException e) {
            model.addAttribute("emailConfirmado", false);
            model.addAttribute("mensagemErro", e.getMessage());
        }
        return "confirmar-email";
    }
}