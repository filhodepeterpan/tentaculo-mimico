package br.com.tentaculomimico.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // por segurança o spring bloqueia tudo por padrão,
    // então esta classe serve como o segurança do nosso sistema.
    // Ela baralha as senhas antes de as guardar no banco de dados e controla
    // a portaria, deixando as rotas do Douglas (como o login e a recuperação de
    // senha) abertas para o público, mas trancando
    // todo o resto da aplicação para exigir que o utilizador já esteja logado.
    //  Por último, desliga um bloqueio padrão do Spring porque vamos usar
    //  os nossos próprios Tokens.

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // O handler entra como parâmetro do @Bean (e não no construtor da classe)
    // para evitar dependência circular: handler -> AuthService -> PasswordEncoder (definido aqui).
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, OAuth2LoginSucessoHandler oauth2SucessoHandler) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(
                        "/",
                        "/login",
                        "/cadastro",
                        "/esqueci-senha",
                        "/redefinir-senha",
                        "/sobre",
                        "/contato",
                        "/cursos",
                        "/cursos/**",
                        "/curso-detalhes",
                        "/curso-detalhes/**",
                        "/cadastro-curso",
                        "/doacoes",
                        "/perfil-aluno/**",
                        "/perfil/**",
                        "/perfil/professor/**",
                        "/perfil/aluno/**",
                        "/perfil-professor/**",
                        "/perfil-editar",
                        "/perfil-excluir",
                        "/matriculas/**",
                        "/sair"
                    )
                    .permitAll()
                    .requestMatchers(
                        "/css/**",
                        "/js/**",
                        "/img/**",
                        "/webjars/**",
                        "/favicon.ico"
                    )
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            )
            // Login com Google pelo fluxo de redirecionamento:
            //   GET /oauth2/authorization/google  -> manda para o Google
            //   GET /login/oauth2/code/google     -> volta do Google (redirect URI)
            // loginPage("/login") é importante: sem ele o Spring gera uma página
            // de login própria em /login e esconde a página Thymeleaf do projeto.
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler(oauth2SucessoHandler)
                .failureHandler((request, response, exception) -> {
                    exception.printStackTrace();
                    response.sendRedirect("/login?erro=google");
                })
            );

        return http.build();
    }
}
