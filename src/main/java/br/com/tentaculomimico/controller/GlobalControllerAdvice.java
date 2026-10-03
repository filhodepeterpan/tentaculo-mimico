package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.SessaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final SessaoService sessaoService;

    @ModelAttribute("usuarioLogado")
    public Usuario getUsuarioLogado() {
        return sessaoService.usuarioLogado();
    }
}