package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import br.com.tentaculomimico.service.AuthService.AutenticacaoException;
import br.com.tentaculomimico.service.EmailService.EmailEnvioException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificacaoEmailService {

    private static final long VALIDADE_HORAS = 24;
    private static final long INTERVALO_REENVIO_MINUTOS = 2;

    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    /** Conta pendente = tem token de verificação. Contas antigas e do Google não têm. */
    public boolean estaPendente(Usuario usuario) {
        return usuario.getTokenVerificacaoEmail() != null;
    }

    /**
     * Gera um token novo, salva e envia o e-mail. Um token novo invalida o link anterior.
     * @return true se o e-mail foi enviado; false se o envio falhou (o erro vai para o log).
     */
    public boolean emitirEEnviar(Usuario usuario) {
        String token = UUID.randomUUID().toString();

        usuario.setTokenVerificacaoEmail(token);
        usuario.setDataExpiracaoVerificacao(LocalDateTime.now().plusHours(VALIDADE_HORAS));
        usuarioRepository.save(usuario);

        try {
            emailService.enviarEmailVerificacao(usuario.getEmail(), token);
            return true;
        } catch (EmailEnvioException e) {
            log.error("Falha ao enviar e-mail de confirmação para o usuário {}", usuario.getId(), e);
            return false;
        }
    }

    /**
     * Reenvia o link só se o último envio foi há mais que o intervalo (evita spam de e-mails).
     * @return true se enviou um novo e-mail.
     */
    public boolean reenviarSeNecessario(Usuario usuario) {
        LocalDateTime expiracao = usuario.getDataExpiracaoVerificacao();

        if (expiracao != null) {
            LocalDateTime ultimoEnvio = expiracao.minusHours(VALIDADE_HORAS);
            if (ultimoEnvio.isAfter(LocalDateTime.now().minusMinutes(INTERVALO_REENVIO_MINUTOS))) {
                return false;
            }
        }
        return emitirEEnviar(usuario);
    }

    /** Valida o token do link. O token só vale uma vez. */
    public void confirmar(String token) {
        // Sem este bloqueio, token nulo faria a busca casar com contas que não têm token.
        if (token == null || token.isBlank()) {
            throw new AutenticacaoException("Link de confirmação inválido ou já utilizado.");
        }

        Usuario usuario = usuarioRepository.findByTokenVerificacaoEmail(token)
                .orElseThrow(() -> new AutenticacaoException("Link de confirmação inválido ou já utilizado."));

        if (usuario.getDataExpiracaoVerificacao() == null
                || usuario.getDataExpiracaoVerificacao().isBefore(LocalDateTime.now())) {
            throw new AutenticacaoException(
                    "Este link de confirmação expirou. Entre com seu e-mail e senha para receber um novo.");
        }

        usuario.setTokenVerificacaoEmail(null);
        usuario.setDataExpiracaoVerificacao(null);
        usuarioRepository.save(usuario);
    }
}