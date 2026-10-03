package br.com.tentaculomimico.service.sessao;

import br.com.tentaculomimico.model.Usuario;

public interface SessaoService {
    Usuario usuarioLogado();

    Usuario exigirUsuarioLogado();

    void iniciarSessao(Usuario usuario);

    void encerrarSessao();
}
