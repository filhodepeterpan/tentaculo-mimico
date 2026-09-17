package br.com.tentaculomimico.service;

import br.com.tentaculomimico.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AuthService {


    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;


    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public String autenticar(String email, String senhaDigitada) {

        Optional<Usuario> optionalUsuario = usuarioRepository.findByEmail(email);
        if (optionalUsuario.isEmpty()) {
            throw new RuntimeException("E-mail ou senha incorretos");
        }
        Usuario usuario = optionalUsuario.get();

        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Conta temporariamente bloqueada. Tente novamente mais tarde.");
        }

        boolean senhaCorreta = passwordEncoder.matches(senhaDigitada, usuario.getAutenticacao().getSenhaHash());

        if (!senhaCorreta) {

            int novasTentativas = usuario.getTentativasLoginFalhas() + 1;
            usuario.setTentativasLoginFalhas(novasTentativas);

            if (novasTentativas >= 5) {
                usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(15));
            }

            usuarioRepository.save(usuario);

            throw new RuntimeException("E-mail ou senha incorretos");

        }
         usuario.setTentativasLoginFalhas(0);
         usuario.setBloqueadoAte(null);
         usuarioRepository.save(usuario);

        return jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());

    }
}