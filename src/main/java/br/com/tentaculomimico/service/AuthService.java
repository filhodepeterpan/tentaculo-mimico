package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.security.JwtTokenProvider;
import br.com.tentaculomimico.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {


    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

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

    public void solicitarRecuperacaoSenha(String email) {
        Optional<Usuario> optionalUsuario = usuarioRepository.findByEmail(email);

        if (optionalUsuario.isEmpty()) {
            return;
        }

        Usuario usuario = optionalUsuario.get();

        String tokenRecuperacao = java.util.UUID.randomUUID().toString();

        usuario.setTokenRecuperacaoSenha(tokenRecuperacao);
        usuario.setDataExpiracaoToken(LocalDateTime.now().plusMinutes(30));

        usuarioRepository.save(usuario);

        emailService.enviarEmailRecuperacao(usuario.getEmail(), tokenRecuperacao);
}

    public void redefinirSenha(String token, String novaSenha, String confirmacaoSenha) {
        if (!novaSenha.equals(confirmacaoSenha)) {
            throw new RuntimeException("As senhas digitadas não coincidem.");
        }

        Optional<Usuario> optionalUsuario = usuarioRepository.findByTokenRecuperacaoSenha(token);

        if (optionalUsuario.isEmpty()) {
            throw new RuntimeException("Token inválido ou expirado.");
        }

        Usuario usuario = optionalUsuario.get();

        if (usuario.getDataExpiracaoToken().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Este link de recuperação já expirou.");
        }

        usuario.getAutenticacao().setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setTokenRecuperacaoSenha(null);
        usuario.setDataExpiracaoToken(null);

        usuarioRepository.save(usuario);
    }
    public String autenticarComGoogle(String email, String nome) {
        Optional<Usuario> optionalUsuario = usuarioRepository.findByEmail(email);
        Usuario usuario;

        if (optionalUsuario.isPresent()) {
             usuario = optionalUsuario.get();
        } else {
            usuario = new Usuario();
            usuario.setEmail(email);
            usuario.setTipoUsuario(TipoUsuario.ALUNO); // Define ALUNO como padrão para novos cadastros
            usuario.setProvedor(Provedor.GOOGLE);


            usuario = usuarioRepository.save(usuario);
        }

        // Independentemente de ser um usuário antigo ou recém-criado, geramos oo JWT
        return jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
    }
    public void cadastrarUsuario(br.com.tentaculomimico.dto.CadastroRequestDTO dados) {
        // vê se o email já existe
        if (usuarioRepository.findByEmail(dados.email()).isPresent()) {
            throw new RuntimeException("Esse e-mail já está em uso!");
        }

        Usuario novoUsuario = new Usuario();
        novoUsuario.setNome(dados.nome());
        novoUsuario.setEmail(dados.email());
        novoUsuario.setDataNascimento(dados.dataNascimento());

        // Se n for info o tipo, vira ALUNO por padrão
        if (dados.tipoUsuario() != null && !dados.tipoUsuario().isEmpty()) {
            novoUsuario.setTipoUsuario(TipoUsuario.valueOf(dados.tipoUsuario().toUpperCase()));
        } else {
            novoUsuario.setTipoUsuario(TipoUsuario.ALUNO);
        }

        // embaralha a senha antes de salvar
        novoUsuario.getAutenticacao().setSenhaHash(passwordEncoder.encode(dados.senha()));

        usuarioRepository.save(novoUsuario);
    }
}