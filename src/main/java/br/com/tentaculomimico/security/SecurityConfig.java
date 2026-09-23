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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/", "/login", "/cadastro", "/sobre", "/contato", "/cursos/**", "/cadastro-curso", "/doacoes").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/img/**", "/webjars/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}