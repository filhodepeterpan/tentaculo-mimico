package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * O login deve chamar iniciarSessao(usuario) após validar a senha.
 */
@Service
public class SessaoService {

    private static final String CHAVE = "usuarioLogadoId";

    private final HttpSession session;
    private final HttpServletRequest request;
    private final UsuarioRepository usuarioRepository;

    public SessaoService(HttpSession session, HttpServletRequest request, UsuarioRepository usuarioRepository) {
        this.session = session;
        this.request = request;
        this.usuarioRepository = usuarioRepository;
    }

    /** Usuário autenticado, ou null se ninguém estiver logado. */
    public Usuario usuarioLogado() {
        Object id = session.getAttribute(CHAVE);
        if (id == null) {
            return null;
        }
        return usuarioRepository.findById(id.toString()).orElse(null);
    }

    public Usuario exigirUsuarioLogado() {
        Usuario usuario = usuarioLogado();
        if (usuario == null) {
            throw new RuntimeException("Você precisa estar logado para fazer isso.");
        }
        return usuario;
    }
    public void iniciarSessao(Usuario usuario) {
        // Troca o ID da sessão no login (evita session fixation).
        // changeSessionId() exige que a sessão exista, por isso o getSession(true) antes.
        request.getSession(true);     // garante que existe uma sessão
        request.changeSessionId();    // troca ABC123 por um ID novo
        session.setAttribute(CHAVE, usuario.getId());
    }
    public void encerrarSessao() {
        session.invalidate();
    }
}
