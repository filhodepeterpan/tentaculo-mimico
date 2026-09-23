package br.com.tentaculomimico.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {
    private final String segredoJWT = "ChaveSuperSecretaDaOngTentaculoMimico2026!!!";

    private final long validadeEmMs = 86400000;

    private SecretKey getSigningKey() {
        byte[] keyBytes = segredoJWT.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String gerarToken(String email, String tipoUsuario) {

        Date agora = new Date();
        Date dataExpiracao = new Date(agora.getTime() + validadeEmMs);

        return Jwts.builder()
                .subject(email) // Diiz QUEM é o dono do token
                .claim("perfil", tipoUsuario) // diz qual é o perfil da pessoa (ALUNO, PROFESSOR, ADMIN)
                .issuedAt(agora) // informa quando que o token foi gerado
                .expiration(dataExpiracao) // Data que ele perde a validade
                .signWith(getSigningKey()) // Assinamos o token para ninguém falsificar!
                .compact(); // Transforma tudo numa string longa
    }
}