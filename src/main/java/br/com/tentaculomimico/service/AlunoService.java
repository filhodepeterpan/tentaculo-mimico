package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.repository.MatriculaRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlunoService {

    private final UsuarioRepository usuarioRepository;
    private final MatriculaRepository matriculaRepository;


    // busca o usuaario atraves do email no banco, se nao encontrar retorna "Usuário não encontrado"
    public Usuario buscarPerfilPorEmail(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));
    }
    public void excluirConta(String email) {
        Usuario usuario = buscarPerfilPorEmail(email);

        // Verifica se a lista de matrículas associadas ao ID do aluno não está vazia
        boolean temMatriculaAtiva = !matriculaRepository.findByAlunoId(usuario.getId()).isEmpty();

        if (temMatriculaAtiva) {
            throw new RuntimeException("Não é possível excluir a conta pois existem matrículas ativas em cursos.");
        }

        usuarioRepository.delete(usuario);
    }
}