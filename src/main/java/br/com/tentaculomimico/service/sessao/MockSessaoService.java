package br.com.tentaculomimico.service.sessao;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
public class MockSessaoService implements SessaoService {

    private final Usuario usuario;

    public MockSessaoService() {
        usuario = new Usuario();

        usuario.setId("mock-user");
        usuario.setNome("Usuário Mock");
        usuario.setCpf("00000000000");
        usuario.setEmail("mock@teste.com");
        usuario.setTipoUsuario(TipoUsuario.ALUNO);
        usuario.setProvedor(Provedor.LOCAL);
    }

    @Override
    public Usuario usuarioLogado() {
        return usuario;
    }

    @Override
    public Usuario exigirUsuarioLogado() {
        return usuario;
    }

    @Override
    public void iniciarSessao(Usuario usuario) {
        // Não faz nada no mock.
    }

    @Override
    public void encerrarSessao() {
        // Não faz nada no mock.
    }
}
