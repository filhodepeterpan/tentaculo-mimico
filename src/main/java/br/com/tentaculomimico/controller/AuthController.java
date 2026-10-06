package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.dto.LoginRequestDTO;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.service.AuthService;
import br.com.tentaculomimico.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessaoService sessaoService; // <-- Adicionado
    private final UsuarioRepository usuarioRepository; // <-- Adicionado

    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("senha") String senha,
            HttpServletResponse response,
            Model model
    ) {
        try {
            String token = authService.autenticar(email, senha);
            Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario != null) {
                sessaoService.iniciarSessao(usuario);
            }

            // Se usar Cookies para guardar o token JWT na aplicação Server-Side:
            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            return "redirect:/cursos";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("erroGeral", "E-mail ou senha inválidos.");
            return "login";
        }
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<String> esqueciSenha(@RequestParam String email) {
        authService.solicitarRecuperacaoSenha(email);
        return ResponseEntity.ok("um link de recuperação será enviado.");
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<String> redefinirSenha(
        @RequestParam String token,
        @RequestParam String novaSenha,
        @RequestParam String confirmacaoSenha
    ) {
        authService.redefinirSenha(token, novaSenha, confirmacaoSenha);
        return ResponseEntity.ok("Senha atualizada com sucesso!");
    }

    @GetMapping("/sair")
    public ResponseEntity<String> logout(
        HttpServletResponse response,
        Model model
    ) throws IOException {
        sessaoService.encerrarSessao();

        // VERIFICAR SE É NECESSÀRIO CRIAR O COOKIE EM PRIMEIRO LUGAR
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);


        return ResponseEntity
            .status(HttpStatus.FOUND)
            .location(URI.create("/login"))
            .build();
    }
}
