package br.com.tentaculomimico.service;

import br.com.tentaculomimico.dto.CadastroRequestDTO;
import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CadastroService {

    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /** Erros por campo. As chaves são: nome, email, cpf, dataNascimento, tipoUsuario, senha, confirmarSenha. */
    public static class CadastroInvalidoException extends RuntimeException {
        private final Map<String, String> erros;

        public CadastroInvalidoException(Map<String, String> erros) {
            super("Cadastro inválido");
            this.erros = erros;
        }

        public Map<String, String> getErros() {
            return erros;
        }
    }

    public Usuario cadastrar(CadastroRequestDTO dto) {
        Map<String, String> erros = new LinkedHashMap<>();

        String nome = dto.nome() == null ? "" : dto.nome().trim();
        String email = dto.email() == null ? "" : dto.email().trim();
        String cpf = dto.cpf() == null ? "" : dto.cpf().replaceAll("\\D", "");
        // O login faz trim() na senha digitada, então o cadastro precisa fazer o mesmo.
        String senha = dto.senha() == null ? "" : dto.senha().trim();
        String confirmar = dto.confirmarSenha() == null ? "" : dto.confirmarSenha().trim();

        // Nome
        if (nome.length() < 3) {
            erros.put("nome", "Informe seu nome completo.");
        }

        // E-mail
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            erros.put("email", "Informe um e-mail válido.");
        } else if (usuarioRepository.findByEmail(email).isPresent()) {
            erros.put("email", "Já existe uma conta com este e-mail.");
        }

        // CPF (guardado só com dígitos, como o UsuarioRepository espera)
        if (!cpfValido(cpf)) {
            erros.put("cpf", "CPF inválido.");
        } else if (usuarioRepository.findByCpf(cpf).isPresent()) {
            erros.put("cpf", "Já existe uma conta com este CPF.");
        }

        // Data de nascimento
        LocalDate dataNascimento = null;
        try {
            dataNascimento = LocalDate.parse(dto.dataNascimento() == null ? "" : dto.dataNascimento().trim());
            if (dataNascimento.isAfter(LocalDate.now()) || dataNascimento.getYear() < 1900) {
                erros.put("dataNascimento", "Data de nascimento inválida.");
            }
        } catch (DateTimeParseException e) {
            erros.put("dataNascimento", "Informe uma data de nascimento válida.");
        }

        // Tipo de usuário: só aluno ou professor podem se cadastrar por aqui.
        TipoUsuario tipo = null;
        if ("aluno".equalsIgnoreCase(dto.tipoUsuario())) {
            tipo = TipoUsuario.ALUNO;
        } else if ("professor".equalsIgnoreCase(dto.tipoUsuario())) {
            tipo = TipoUsuario.PROFESSOR;
        } else {
            erros.put("tipoUsuario", "Selecione o tipo de cadastro.");
        }

        // Senha
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            erros.put("senha", "A senha deve ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
        }
        if (!senha.equals(confirmar)) {
            erros.put("confirmarSenha", "As senhas não coincidem.");
        }

        if (!erros.isEmpty()) {
            throw new CadastroInvalidoException(erros);
        }

        Autenticacao autenticacao = new Autenticacao();
        autenticacao.setProvedor(Provedor.LOCAL);
        autenticacao.setSenhaHash(passwordEncoder.encode(senha));

        Usuario usuario = new Usuario();
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setCpf(cpf);
        usuario.setDataNascimento(dataNascimento);
        usuario.setTipoUsuario(tipo);
        usuario.setAutenticacao(autenticacao);

        return usuarioRepository.save(usuario);
    }

    private boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}")) {
            return false;
        }
        // rejeita sequências como 111.111.111-11
        if (cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digitoVerificador(cpf, 9) == cpf.charAt(9) - '0'
                && digitoVerificador(cpf, 10) == cpf.charAt(10) - '0';
    }

    private int digitoVerificador(String cpf, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (cpf.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
