package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.TipoUsuario;
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

    /** Lançada quando a ação exige CPF e data de nascimento e a conta ainda não tem (conta Google). */
    public static class CadastroIncompletoException extends RuntimeException {
        public CadastroIncompletoException() {
            super("Complete seu cadastro (CPF e data de nascimento) para continuar.");
        }
    }

    /** true se há usuário logado (não administrador) sem CPF ou sem data de nascimento. */
    public static boolean cadastroIncompleto(Usuario usuario) {
        if (usuario == null || usuario.getTipoUsuario() == TipoUsuario.ADMINISTRADOR) {
            return false;
        }
        return usuario.getCpf() == null || usuario.getCpf().isBlank()
                || usuario.getDataNascimento() == null;
    }

    public boolean cadastroIncompleto() {
        return cadastroIncompleto(usuarioLogado());
    }

    /** Igual a exigirUsuarioLogado, mas também lança CadastroIncompletoException se faltar CPF/data. */
    public Usuario exigirCadastroCompleto() {
        Usuario usuario = exigirUsuarioLogado();
        if (cadastroIncompleto(usuario)) {
            throw new CadastroIncompletoException();
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
