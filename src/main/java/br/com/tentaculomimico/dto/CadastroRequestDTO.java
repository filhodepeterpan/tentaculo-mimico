package br.com.tentaculomimico.dto;

/**
 * Campos do formulário de cadastro. Tudo String de propósito: a conversão
 * (data, tipo de usuário) e a validação ficam no CadastroService, para que
 * um valor inválido vire mensagem de erro no formulário e não um 400.
 */
public record CadastroRequestDTO(
        String nome,
        String email,
        String cpf,
        String dataNascimento,
        String tipoUsuario,
        String senha,
        String confirmarSenha
) {
}
