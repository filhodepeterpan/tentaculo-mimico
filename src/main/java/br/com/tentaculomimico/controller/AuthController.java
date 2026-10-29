package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.AuthService;
import br.com.tentaculomimico.service.AuthService.AutenticacaoException;
import br.com.tentaculomimico.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessaoService sessaoService;

    @PostMapping("/login")
    public String login(
            @RequestParam("email") String email,
            @RequestParam("senha") String senha,
            HttpServletResponse response,
            Model model
    ) {
        try {
            Usuario usuario = authService.validarCredenciais(email, senha);
            sessaoService.iniciarSessao(usuario);

            Cookie cookie = new Cookie("jwt", authService.gerarToken(usuario));
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            return "redirect:/cursos";
        } catch (AutenticacaoException e) {
            // Mensagem segura: credenciais inválidas, conta bloqueada (com o tempo restante), etc.
            model.addAttribute("erroGeral", e.getMessage());
            return "login";
        } catch (Exception e) {
            log.error("Erro inesperado no login", e);
            model.addAttribute("erroGeral", "Não foi possível entrar agora. Tente novamente.");
            return "login";
        }
    }

    @GetMapping("/recuperar-senha")
    public String paginaRecuperarSenha() {
        return "recuperar-senha";
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<String> esqueciSenha(@RequestParam String email) {
        authService.solicitarRecuperacaoSenha(email);
        return ResponseEntity.ok("Se o e-mail estiver cadastrado, enviaremos um link de recuperação.");
    }

    @GetMapping("/redefinir-senha")
    public String paginaRedefinirSenha(
            @RequestParam(required = false) String token,
            Model model
    ) {
        // O token vem no link do e-mail (/redefinir-senha?token=...).
        // A validação real acontece no POST; aqui só repassamos pra página.
        model.addAttribute("token", token);
        return "redefinir-senha";
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<String> redefinirSenha(
            @RequestParam String token,
            @RequestParam String novaSenha,
            @RequestParam String confirmacaoSenha
    ) {
        try {
            authService.redefinirSenha(token, novaSenha, confirmacaoSenha);
            return ResponseEntity.ok("Senha atualizada com sucesso!");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/sair")
    public ResponseEntity<String> logout(HttpServletResponse response) {
        sessaoService.encerrarSessao();

        // O navegador só apaga um cookie se o servidor mandar outro com o MESMO nome e path
        // e maxAge 0. Não existe "comando de apagar" por isso criamos o cookie aqui.
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