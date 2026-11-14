package br.com.tentaculomimico.service;

import br.com.tentaculomimico.dto.CadastroRequestDTO;
import br.com.tentaculomimico.dto.GoogleCadastroPendente;
import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.UsuarioRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
@Getter
@Service
@RequiredArgsConstructor
public class CadastroService {

    public static final int TAMANHO_MINIMO_SENHA = 8;

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
        String email = dto.email() == null ? "" : dto.email().trim().toLowerCase();
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
        validarCpf(cpf, null, erros);

        // Data de nascimento
        LocalDate dataNascimento = validarDataNascimento(dto.dataNascimento(), erros);

        // Tipo de usuário: só aluno ou professor podem se cadastrar por aqui.
        TipoUsuario tipo = resolverTipo(dto.tipoUsuario(), erros);

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

        try {
            return usuarioRepository.save(usuario);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            erros.put("email", "Já existe uma conta com este e-mail.");
            throw new CadastroInvalidoException(erros);
        }
    }

    /**
     * Segunda etapa do cadastro com Google: cria a conta só agora, com o tipo escolhido,
     * CPF e data de nascimento (a do Google, se veio; senão a informada na página).
     * Lança CadastroInvalidoException (chaves: tipoUsuario, cpf, dataNascimento, email).
     */
    public Usuario cadastrarComGoogle(GoogleCadastroPendente pendente, String tipoUsuario,
                                      String cpfBruto, String dataNascimentoBruta) {
        Map<String, String> erros = new LinkedHashMap<>();

        String email = pendente.email().trim().toLowerCase();
        String cpf = cpfBruto == null ? "" : cpfBruto.replaceAll("\\D", "");

        TipoUsuario tipo = resolverTipo(tipoUsuario, erros);
        validarCpf(cpf, null, erros);

        LocalDate dataNascimento = pendente.dataNascimento();
        if (dataNascimento == null) {
            dataNascimento = validarDataNascimento(dataNascimentoBruta, erros);
        }

        if (usuarioRepository.findByEmail(email).isPresent()) {
            erros.put("email", "Já existe uma conta com este e-mail.");
        }

        if (!erros.isEmpty()) {
            throw new CadastroInvalidoException(erros);
        }

        Autenticacao auth = new Autenticacao();
        auth.setProvedor(Provedor.GOOGLE);
        auth.setProvedorId(pendente.googleId());

        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNome(pendente.nome() != null && !pendente.nome().isBlank() ? pendente.nome().trim() : email);
        usuario.setCpf(cpf);
        usuario.setDataNascimento(dataNascimento);
        usuario.setTipoUsuario(tipo);
        usuario.setAutenticacao(auth);

        try {
            return usuarioRepository.save(usuario);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            erros.put("email", "Já existe uma conta com este e-mail.");
            throw new CadastroInvalidoException(erros);
        }
    }

    /**
     * Completa uma conta que está sem CPF e/ou sem data de nascimento (conta Google antiga,
     * ou criada antes desta regra). Só pede o que falta: o que já está salvo não é alterado.
     * Lança CadastroInvalidoException (chaves: cpf, dataNascimento).
     */
    public Usuario completarCadastro(Usuario usuario, String cpfBruto, String dataNascimentoBruta) {
        Map<String, String> erros = new LinkedHashMap<>();

        boolean faltaCpf = usuario.getCpf() == null || usuario.getCpf().isBlank();
        boolean faltaData = usuario.getDataNascimento() == null;

        String cpf = cpfBruto == null ? "" : cpfBruto.replaceAll("\\D", "");
        if (faltaCpf) {
            validarCpf(cpf, usuario.getId(), erros);
        }

        LocalDate dataNascimento = null;
        if (faltaData) {
            dataNascimento = validarDataNascimento(dataNascimentoBruta, erros);
        }

        if (!erros.isEmpty()) {
            throw new CadastroInvalidoException(erros);
        }

        if (faltaCpf) {
            usuario.setCpf(cpf);
        }
        if (faltaData) {
            usuario.setDataNascimento(dataNascimento);
        }
        return usuarioRepository.save(usuario);
    }

    /** idDoProprio: id do usuário que está salvando o CPF (para não acusar duplicidade consigo mesmo); null no cadastro novo. */
    private void validarCpf(String cpf, String idDoProprio, Map<String, String> erros) {
        if (!cpfValido(cpf)) {
            erros.put("cpf", "CPF inválido.");
            return;
        }
        usuarioRepository.findByCpf(cpf)
                .filter(outro -> !outro.getId().equals(idDoProprio))
                .ifPresent(outro -> erros.put("cpf", "Já existe uma conta com este CPF."));
    }

    /** Devolve a data, ou null (com o erro registrado) se estiver vazia, mal formada ou fora do intervalo. */
    private LocalDate validarDataNascimento(String texto, Map<String, String> erros) {
        try {
            LocalDate data = LocalDate.parse(texto == null ? "" : texto.trim());
            if (data.isAfter(LocalDate.now()) || data.getYear() < 1900) {
                erros.put("dataNascimento", "Data de nascimento inválida.");
                return null;
            }
            return data;
        } catch (DateTimeParseException e) {
            erros.put("dataNascimento", "Informe uma data de nascimento válida.");
            return null;
        }
    }

    /** Só aluno ou professor podem se cadastrar por aqui. */
    private TipoUsuario resolverTipo(String tipo, Map<String, String> erros) {
        if ("aluno".equalsIgnoreCase(tipo)) {
            return TipoUsuario.ALUNO;
        }
        if ("professor".equalsIgnoreCase(tipo)) {
            return TipoUsuario.PROFESSOR;
        }
        erros.put("tipoUsuario", "Selecione o tipo de cadastro.");
        return null;
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
