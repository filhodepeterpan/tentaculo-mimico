package br.com.tentaculomimico.security;

import br.com.tentaculomimico.dto.GoogleCadastroPendente;
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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Roda depois que o Google autenticou a pessoa (fluxo de redirecionamento).
 *
 *  - Conta Google já existente: abre a sessão, grava o cookie JWT e manda para /cursos.
 *  - Primeiro acesso: NÃO cria a conta. Guarda os dados do Google na sessão e manda para
 *    /cadastro/google, onde a pessoa escolhe aluno ou professor e informa CPF
 *    (e data de nascimento, se o Google não mandou).
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
            Optional<Usuario> existente = authService.buscarUsuarioGoogle(google.getEmail(), google.getSubject());

            if (existente.isPresent()) {
                Usuario usuario = existente.get();
                sessaoService.iniciarSessao(usuario);

                String token = jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
                Cookie cookie = new Cookie("jwt", token);
                cookie.setHttpOnly(true);
                cookie.setPath("/");
                response.addCookie(cookie);

                response.sendRedirect("/cursos");
                return;
            }

            // Primeiro acesso: a conta só é criada depois da escolha de aluno/professor.
            GoogleCadastroPendente pendente = new GoogleCadastroPendente(
                    google.getEmail(),
                    google.getFullName(),
                    google.getSubject(),
                    lerDataNascimento(google));
            request.getSession(true).setAttribute(GoogleCadastroPendente.CHAVE_SESSAO, pendente);

            response.sendRedirect("/cadastro/google");
        } catch (AuthService.ContaGoogleException e) {
            // Erro que a pessoa precisa ver (ex.: e-mail já cadastrado com senha): login.html mostra o alerta.
            response.sendRedirect("/login?erro=" + e.getCodigo());
        } catch (Exception e) {
            e.printStackTrace();
            response.sendRedirect("/login?erro=google");
        }
    }

    /**
     * O Google só manda "birthdate" no token se a conta permitir E o escopo pedir; com os escopos
     * atuais (openid, profile, email) quase nunca vem. Quando não vem (ou vem incompleta, como
     * "0000-05-17" ou só o ano), devolve null e a página de cadastro pede a data.
     */
    private LocalDate lerDataNascimento(OidcUser google) {
        String texto = google.getBirthdate();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            LocalDate data = LocalDate.parse(texto.trim());
            if (data.isAfter(LocalDate.now()) || data.getYear() < 1900) {
                return null;
            }
            return data;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
