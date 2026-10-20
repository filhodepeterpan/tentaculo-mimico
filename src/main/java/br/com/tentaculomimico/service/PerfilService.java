package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
import br.com.tentaculomimico.model.enums.TipoUsuario;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PerfilService {

    private static final int TAMANHO_MINIMO_SENHA = 8;
    private static final Pattern PADRAO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UsuarioRepository usuarioRepository;
    private final CursoRepository cursoRepository;
    private final CursoService cursoService;
    private final MatriculaService matriculaService;
    private final SessaoService sessaoService;
    private final ImagemService imagemService;
    private final PasswordEncoder passwordEncoder;

    public PerfilService(UsuarioRepository usuarioRepository, CursoRepository cursoRepository,
                         CursoService cursoService, MatriculaService matriculaService,
                         SessaoService sessaoService, ImagemService imagemService,
                         PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.cursoRepository = cursoRepository;
        this.cursoService = cursoService;
        this.matriculaService = matriculaService;
        this.sessaoService = sessaoService;
        this.imagemService = imagemService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Erros de validação por campo. As chaves são: nome, email, cpf,
     * dataNascimento, senhaAtual, novaSenha, confirmarNovaSenha.
     */
    public static class DadosInvalidosException extends RuntimeException {
        private final Map<String, String> erros;

        public DadosInvalidosException(Map<String, String> erros) {
            super("Corrija os campos destacados.");
            this.erros = erros;
        }

        public Map<String, String> getErros() {
            return erros;
        }
    }

    public void atualizarDadosPessoais(String nome, MultipartFile fotoPerfil) {
        Usuario usuario = sessaoService.exigirUsuarioLogado();

        if (nome == null || nome.isBlank()) {
            throw new RuntimeException("O nome não pode ficar em branco.");
        }
        usuario.setNome(nome.trim());

        String urlFoto = imagemService.enviar(fotoPerfil, "perfis");
        if (urlFoto != null) {
            usuario.setFotoPerfil(urlFoto);
        }

        usuarioRepository.save(usuario);
    }

    /**
     * Edição completa do perfil do usuário logado ou alvo (via Admin).
     */
    public void atualizarPerfil(String usuarioId, String nome, String email, String cpf, String dataNascimento,
                                String senhaAtual, String novaSenha, String confirmarNovaSenha,
                                MultipartFile fotoPerfil) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        Usuario usuario = usuarioEditavel(usuarioId);
        boolean editandoOutro = !logado.getId().equals(usuario.getId());

        Map<String, String> erros = new LinkedHashMap<>();

        // Nome
        String nomeLimpo = nome == null ? "" : nome.trim();
        if (nomeLimpo.isEmpty()) {
            erros.put("nome", "O nome não pode ficar em branco.");
        }

        // E-mail
        String emailLimpo = email == null ? "" : email.trim();
        if (emailLimpo.isEmpty()) {
            erros.put("email", "Informe seu e-mail.");
        } else if (!emailLimpo.equals(usuario.getEmail())) {
            if (!PADRAO_EMAIL.matcher(emailLimpo).matches()) {
                erros.put("email", "Informe um e-mail válido.");
            } else if (usuarioRepository.findByEmail(emailLimpo)
                    .filter(outro -> !outro.getId().equals(usuario.getId())).isPresent()) {
                erros.put("email", "Este e-mail já está em uso.");
            }
        }

        // CPF
        String cpfDigitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
        String cpfAtual = usuario.getCpf() == null ? "" : usuario.getCpf().replaceAll("\\D", "");
        boolean cpfMudou = !cpfDigitos.equals(cpfAtual);
        if (cpfDigitos.isEmpty()) {
            erros.put("cpf", "Informe seu CPF.");
        } else if (cpfMudou) {
            if (cpfDigitos.length() != 11) {
                erros.put("cpf", "O CPF deve ter 11 dígitos.");
            } else if (usuarioRepository.findByCpf(cpfDigitos)
                    .filter(outro -> !outro.getId().equals(usuario.getId())).isPresent()) {
                erros.put("cpf", "Este CPF já está cadastrado.");
            }
        }

        // Data de nascimento
        LocalDate nascimento = null;
        if (dataNascimento == null || dataNascimento.isBlank()) {
            erros.put("dataNascimento", "Informe sua data de nascimento.");
        } else {
            try {
                nascimento = LocalDate.parse(dataNascimento.trim());
                if (nascimento.isAfter(LocalDate.now())) {
                    erros.put("dataNascimento", "A data de nascimento não pode estar no futuro.");
                }
            } catch (DateTimeParseException e) {
                erros.put("dataNascimento", "Data de nascimento inválida.");
            }
        }

        // Senha (opcional)
        boolean trocarSenha = !editandoOutro && (!vazio(novaSenha) || !vazio(confirmarNovaSenha));
        if (trocarSenha) {
            Autenticacao auth = usuario.getAutenticacao();
            boolean contaLocal = auth == null || auth.getProvedor() == Provedor.LOCAL;

            if (!contaLocal) {
                erros.put("novaSenha", "Sua conta usa login com Google; a senha é gerenciada por lá.");
            } else {
                String hashAtual = auth != null ? auth.getSenhaHash() : null;
                if (vazio(senhaAtual)) {
                    erros.put("senhaAtual", "Informe sua senha atual para trocar a senha.");
                } else if (hashAtual == null || !passwordEncoder.matches(senhaAtual, hashAtual)) {
                    erros.put("senhaAtual", "Senha atual incorreta.");
                }
                if (vazio(novaSenha) || novaSenha.length() < TAMANHO_MINIMO_SENHA) {
                    erros.put("novaSenha", "A nova senha deve ter ao menos " + TAMANHO_MINIMO_SENHA + " caracteres.");
                }
                if (!Objects.equals(novaSenha, confirmarNovaSenha)) {
                    erros.put("confirmarNovaSenha", "As senhas não conferem.");
                }
            }
        }

        if (!erros.isEmpty()) {
            throw new DadosInvalidosException(erros);
        }

        usuario.setNome(nomeLimpo);
        usuario.setEmail(emailLimpo);
        if (cpfMudou) {
            usuario.setCpf(cpfDigitos);
        }
        usuario.setDataNascimento(nascimento);

        if (trocarSenha) {
            if (usuario.getAutenticacao() == null) {
                usuario.setAutenticacao(new Autenticacao());
            }
            usuario.getAutenticacao().setSenhaHash(passwordEncoder.encode(novaSenha));
        }

        String urlFoto = imagemService.enviar(fotoPerfil, "perfis");
        if (urlFoto != null) {
            usuario.setFotoPerfil(urlFoto);
        }

        usuarioRepository.save(usuario);
    }

    /**
     * Exclui a conta do usuário logado (apaga o Usuario de vez) e encerra a sessão.
     */
    public void excluirContaDoUsuarioLogado() {
        Usuario usuario = sessaoService.exigirUsuarioLogado();

        // Regra do TASK-62: Administrador não pode excluir a própria conta por aqui
        if (usuario.getTipoUsuario() == TipoUsuario.ADMINISTRADOR ||
                "ADMINISTRADOR".equalsIgnoreCase(usuario.getTipoUsuario().name())) {
            throw new RuntimeException("Administradores não podem excluir suas próprias contas.");
        }

        apagarUsuarioComDependencias(usuario);
        sessaoService.encerrarSessao();
    }

    /**
     * Exclui um usuário específico através da visão de Administrador.
     */
    public void excluirUsuarioComoAdmin(String usuarioId) {
        Usuario admin = sessaoService.exigirUsuarioLogado();
        if (admin.getTipoUsuario() != TipoUsuario.ADMINISTRADOR) {
            throw new RuntimeException("Você não tem permissão para excluir este usuário.");
        }
        if (admin.getId().equals(usuarioId)) {
            throw new RuntimeException("O administrador não pode excluir a própria conta.");
        }
        Usuario alvo = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));
        apagarUsuarioComDependencias(alvo);
    }

    private void apagarUsuarioComDependencias(Usuario usuario) {
        String tipo = usuario.getTipoUsuario().name();
        if ("PROFESSOR".equalsIgnoreCase(tipo)) {
            matriculaService.exigirProfessorPodeExcluirConta(usuario.getId());
            for (Curso curso : cursoRepository.findByProfessorId(usuario.getId())) {
                cursoService.excluirCurso(curso.getId());
            }
        } else if ("ALUNO".equalsIgnoreCase(tipo)) {
            matriculaService.exigirAlunoPodeExcluirConta(usuario.getId());
            matriculaService.cancelarEmAbertoDoAluno(usuario.getId());
        }
        usuarioRepository.delete(usuario);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    private Usuario usuarioEditavel(String usuarioId) {
        Usuario logado = sessaoService.exigirUsuarioLogado();
        if (usuarioId == null || usuarioId.isBlank() || usuarioId.equals(logado.getId())) {
            return logado;
        }
        if (logado.getTipoUsuario() != TipoUsuario.ADMINISTRADOR) {
            throw new RuntimeException("Você não tem permissão para editar este usuário.");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));
    }
}