package br.com.tentaculomimico.security;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.AuthService;
import br.com.tentaculomimico.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Roda depois que o Google autenticou a pessoa (fluxo de redirecionamento).
 * Busca ou cria o Usuario, abre a sessão, grava o cookie JWT e manda para /cursos.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSucessoHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final SessaoService sessaoService;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        if (!(authentication.getPrincipal() instanceof OidcUser google)
                || google.getEmail() == null
                || !Boolean.TRUE.equals(google.getEmailVerified())) {
            response.sendRedirect("/login?erro=google");
            return;
        }

        try {
            Usuario usuario = authService.obterOuCriarUsuarioGoogle(
                    google.getEmail(), google.getFullName(), google.getSubject());

            sessaoService.iniciarSessao(usuario);

            String token = jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            response.sendRedirect("/cursos");
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/login?erro=google");
        }
    }
}
