package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
            System.out.println(">>> DIAGNÓSTICO: Nenhum usuário encontrado para o e-mail: " + email);
            throw new RuntimeException("E-mail ou senha incorretos");
        }
        Usuario usuario = optionalUsuario.get();

        // 1. Verifica se a conta está temporariamente bloqueada
        if (usuario.getBloqueadoAte() != null && usuario.getBloqueadoAte().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("Conta temporariamente bloqueada. Tente novamente mais tarde.");
        }

        // 2. Valida se o usuário possui dados de autenticação
        if (usuario.getAutenticacao() == null || usuario.getAutenticacao().getSenhaHash() == null) {
            if (usuario.getAutenticacao() != null && usuario.getAutenticacao().getProvedor() == Provedor.GOOGLE) {
                throw new RuntimeException("Esta conta foi criada usando o Google. Utilize o botão 'Entrar com Google'.");
            }
            System.out.println(">>> DIAGNÓSTICO: Objeto Autenticacao ou senhaHash está NULO para o e-mail: " + email);
            throw new RuntimeException("E-mail ou senha incorretos");
        }

        String senhaTratada = senhaDigitada != null ? senhaDigitada.trim() : "";
        String hashDoBanco = usuario.getAutenticacao().getSenhaHash();

        System.out.println("===== DIAGNÓSTICO DE LOGIN =====");
        System.out.println("E-mail encontrado: " + usuario.getEmail());
        System.out.println("Senha digitada (bruta): '" + senhaDigitada + "'");
        System.out.println("Senha digitada (com trim): '" + senhaTratada + "'");
        System.out.println("Hash carregado do Mongo: '" + hashDoBanco + "'");

        // 3. Valida a senha usando o PasswordEncoder
        boolean senhaCorreta = passwordEncoder.matches(senhaTratada, hashDoBanco);
        System.out.println("Resultado do BCrypt matches: " + senhaCorreta);
        System.out.println("================================");

        // ====================================================================================
        // ATENÇÃO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // BLOCO TEMPORÁRIO DE AUTO-REPARO DE SENHA DE TESTE (123456)
        // REMOVER COMPLETAMENTE ESTE BLOCO ABAIXO ANTES DE IR PARA AMBIENTE DE PRODUÇÃO!
        // INÍCIO DO BLOCO A REMOVER --->
        // ====================================================================================
        if (!senhaCorreta && "123456".equals(senhaTratada)) {
            System.out.println(">>> REPARANDO HASH DE TESTE PARA A SENHA 123456...");
            String novoHashNativo = passwordEncoder.encode("123456");

            if (usuario.getAutenticacao() == null) {
                usuario.setAutenticacao(new Autenticacao());
            }
            usuario.getAutenticacao().setSenhaHash(novoHashNativo);
            usuario.getAutenticacao().setProvedor(Provedor.LOCAL);
            usuario.setTentativasLoginFalhas(0);
            usuario.setBloqueadoAte(null);

            usuarioRepository.save(usuario);

            System.out.println(">>> NOVO HASH GERADO PELA JVM E SALVO NO MONGO: " + novoHashNativo);
            senhaCorreta = true;
        }
        // ====================================================================================
        // <--- FIM DO BLOCO A REMOVER
        // ATENÇÃO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // ====================================================================================

        if (!senhaCorreta) {
            int novasTentativas = usuario.getTentativasLoginFalhas() + 1;
            usuario.setTentativasLoginFalhas(novasTentativas);

            if (novasTentativas >= 5) {
                usuario.setBloqueadoAte(LocalDateTime.now().plusMinutes(15));
            }

            usuarioRepository.save(usuario);
            throw new RuntimeException("E-mail ou senha incorretos");
        }

        // 4. Sucesso: Reseta contadores de falhas
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

        if (usuario.getAutenticacao() == null) {
            usuario.setAutenticacao(new Autenticacao());
        }

        usuario.getAutenticacao().setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.getAutenticacao().setProvedor(Provedor.LOCAL);
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
            usuario.setNome(nome);
            usuario.setTipoUsuario(TipoUsuario.ALUNO);

            Autenticacao auth = new Autenticacao();
            auth.setProvedor(Provedor.GOOGLE);
            usuario.setAutenticacao(auth);

            usuario = usuarioRepository.save(usuario);
        }

        return jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
    }
}