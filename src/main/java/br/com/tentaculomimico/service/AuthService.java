package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_TENTATIVAS_FALHAS = 5;
    private static final long BLOQUEIO_MINUTOS = 15;
    private static final long VALIDADE_TOKEN_RECUPERACAO_MINUTOS = 30;
    private static final String MSG_CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    /**
     * Erro de autenticação cuja mensagem é segura para mostrar ao usuário.
     * Qualquer outra exceção é tratada como erro inesperado pelo controller.
     */
    public static class AutenticacaoException extends RuntimeException {
        public AutenticacaoException(String mensagem) {
            super(mensagem);
        }
    }

    /**
     * Valida e-mail e senha, controla o bloqueio por tentativas e devolve o usuário autenticado.
     */
    public Usuario validarCredenciais(String email, String senhaDigitada) {
        Usuario usuario = buscarPorEmail(email)
                .orElseThrow(() -> new AutenticacaoException(MSG_CREDENCIAIS_INVALIDAS));

        // Se o bloqueio já expirou, recomeça a contagem do zero.
        LocalDateTime agora = LocalDateTime.now();
        if (usuario.getBloqueadoAte() != null) {
            if (usuario.getBloqueadoAte().isAfter(agora)) {
                throw contaBloqueada(usuario.getBloqueadoAte());
            }
            usuario.setBloqueadoAte(null);
            usuario.setTentativasLoginFalhas(0);
        }

        // Valida se o usuário possui dados de autenticação
        if (usuario.getAutenticacao() == null || usuario.getAutenticacao().getSenhaHash() == null) {
            if (usuario.getAutenticacao() != null && usuario.getAutenticacao().getProvedor() == Provedor.GOOGLE) {
                throw new AutenticacaoException("Esta conta foi criada usando o Google. Utilize o botão 'Entrar com Google'.");
            }
            log.warn("Login: usuário {} sem senhaHash e sem provedor Google", usuario.getId());
            throw new AutenticacaoException(MSG_CREDENCIAIS_INVALIDAS);
        }

        // Valida a senha usando o PasswordEncoder
        boolean senhaCorreta = passwordEncoder.matches(
                normalizarSenha(senhaDigitada),
                usuario.getAutenticacao().getSenhaHash());

        if (!senhaCorreta) {
            int novasTentativas = usuario.getTentativasLoginFalhas() + 1;

            if (novasTentativas >= MAX_TENTATIVAS_FALHAS) {
                usuario.setBloqueadoAte(agora.plusMinutes(BLOQUEIO_MINUTOS));
                usuario.setTentativasLoginFalhas(0); // recomeça do zero quando o bloqueio acabar
                usuarioRepository.save(usuario);
                throw contaBloqueada(usuario.getBloqueadoAte());
            }

            usuario.setTentativasLoginFalhas(novasTentativas);
            usuarioRepository.save(usuario);
            throw new AutenticacaoException(MSG_CREDENCIAIS_INVALIDAS);
        }

        // reseta contadores de falhas
        usuario.setTentativasLoginFalhas(0);
        usuario.setBloqueadoAte(null);
        usuarioRepository.save(usuario);

        return usuario;
    }

    public String gerarToken(Usuario usuario) {
        return jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
    }
    public String autenticar(String email, String senhaDigitada) {
        return gerarToken(validarCredenciais(email, senhaDigitada));
    }

    public void solicitarRecuperacaoSenha(String email) {
        Optional<Usuario> optionalUsuario = buscarPorEmail(email);

        if (optionalUsuario.isEmpty()) {
            return;
        }

        Usuario usuario = optionalUsuario.get();
        String tokenRecuperacao = UUID.randomUUID().toString();

        usuario.setTokenRecuperacaoSenha(tokenRecuperacao);
        usuario.setDataExpiracaoToken(LocalDateTime.now().plusMinutes(VALIDADE_TOKEN_RECUPERACAO_MINUTOS));
        usuarioRepository.save(usuario);

        try {
            emailService.enviarEmailRecuperacao(usuario.getEmail(), tokenRecuperacao);
        } catch (MailException e) {
            log.error("Falha ao enviar e-mail de recuperação para o usuário {}", usuario.getId(), e);
        }
    }

    public void redefinirSenha(String token, String novaSenha, String confirmacaoSenha) {
        String novaSenhaTratada = normalizarSenha(novaSenha);

        if (novaSenhaTratada.length() < CadastroService.TAMANHO_MINIMO_SENHA) {
            throw new AutenticacaoException(
                    "A senha deve ter pelo menos " + CadastroService.TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        if (!novaSenhaTratada.equals(normalizarSenha(confirmacaoSenha))) {
            throw new AutenticacaoException("As senhas digitadas não coincidem.");
        }
        if (token == null || token.isBlank()) {
            throw new AutenticacaoException("Token inválido ou expirado.");
        }

        Usuario usuario = usuarioRepository.findByTokenRecuperacaoSenha(token)
                .orElseThrow(() -> new AutenticacaoException("Token inválido ou expirado."));

        if (usuario.getDataExpiracaoToken() == null
                || usuario.getDataExpiracaoToken().isBefore(LocalDateTime.now())) {
            throw new AutenticacaoException("Este link de recuperação já expirou.");
        }

        if (usuario.getAutenticacao() == null) {
            usuario.setAutenticacao(new Autenticacao());
        }

        usuario.getAutenticacao().setSenhaHash(passwordEncoder.encode(novaSenhaTratada));
        usuario.getAutenticacao().setProvedor(Provedor.LOCAL);
        usuario.setTokenRecuperacaoSenha(null);
        usuario.setDataExpiracaoToken(null);

        // Quem redefine a senha também sai do bloqueio por tentativas.
        usuario.setTentativasLoginFalhas(0);
        usuario.setBloqueadoAte(null);

        usuarioRepository.save(usuario);
    }

    /**
     * Usado pelo OAuth2LoginSucessoHandler depois que o Google autenticou a pessoa.
     * Cria a conta (como ALUNO) no primeiro acesso; nos seguintes, só devolve a existente.
     */
    public Usuario obterOuCriarUsuarioGoogle(String email, String nome, String googleId) {
        Optional<Usuario> optionalUsuario = usuarioRepository.findByEmail(email);

        if (optionalUsuario.isPresent()) {
            Usuario usuario = optionalUsuario.get();
            Autenticacao auth = usuario.getAutenticacao();

            // Conta criada com senha: não vinculamos ao Google automaticamente, porque o
            // e-mail dela nunca foi confirmado (ver observação na resposta).
            if (auth == null || auth.getProvedor() != Provedor.GOOGLE) {
                throw new RuntimeException("Já existe uma conta com este e-mail. Entre com e-mail e senha.");
            }

            if (auth.getProvedorId() == null) {
                auth.setProvedorId(googleId);
                usuarioRepository.save(usuario);
            } else if (!auth.getProvedorId().equals(googleId)) {
                throw new RuntimeException("Esta conta está vinculada a outra conta Google.");
            }
            return usuario;
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNome(nome);
        usuario.setTipoUsuario(TipoUsuario.ALUNO);

        Autenticacao auth = new Autenticacao();
        auth.setProvedor(Provedor.GOOGLE);
        auth.setProvedorId(googleId);
        usuario.setAutenticacao(auth);

        return usuarioRepository.save(usuario);
    }


    /** Regra única de tratamento de senha (login e redefinição usam a mesma). */
    private String normalizarSenha(String senha) {
        return senha != null ? senha.trim() : "";
    }

    /** Busca o e-mail em minúsculas primeiro e, se não achar, do jeito que foi digitado (contas antigas). */
    private Optional<Usuario> buscarPorEmail(String email) {
        String tratado = email != null ? email.trim() : "";
        return usuarioRepository.findByEmail(tratado.toLowerCase())
                .or(() -> usuarioRepository.findByEmail(tratado));
    }

    private AutenticacaoException contaBloqueada(LocalDateTime bloqueadoAte) {
        long segundos = Duration.between(LocalDateTime.now(), bloqueadoAte).getSeconds();
        long minutos = Math.max(1, (segundos + 59) / 60); // arredonda para cima
        return new AutenticacaoException(
                "Conta bloqueada por excesso de tentativas. Tente novamente em " + minutos + " minuto(s).");
    }
}