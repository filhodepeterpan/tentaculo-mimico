package br.com.tentaculomimico.service;

import br.com.tentaculomimico.model.Autenticacao;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.model.enums.Provedor;
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

    // ASSUMIDO: não vi as regras de senha do cadastro. Se forem outras, mude aqui.
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
        if (urlFoto != null) { // sem arquivo novo, mantém a foto atual
            usuario.setFotoPerfil(urlFoto);
        }

        usuarioRepository.save(usuario);
    }

    /**
     * Edição completa do perfil do usuário logado. O tipo de usuário não muda.
     *
     * - E-mail e CPF só são revalidados (formato e unicidade) se mudaram, pra
     *   não travar contas antigas com dados fora do padrão.
     * - CPF é guardado só com dígitos.
     * - Senha é opcional: só troca se vier nova senha (ou a confirmação).
     *   Exige a senha atual, a nova com tamanho mínimo e as duas iguais.
     *   Contas de login com Google não têm senha local e não podem trocar.
     * - Todos os erros são coletados e lançados juntos (DadosInvalidosException).
     * - A foto só sobe pro Cloudinary depois que tudo passou na validação.
     */
    public void atualizarPerfil(String nome, String email, String cpf, String dataNascimento,
                                String senhaAtual, String novaSenha, String confirmarNovaSenha,
                                MultipartFile fotoPerfil) {
        Usuario usuario = sessaoService.exigirUsuarioLogado();
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
        boolean trocarSenha = !vazio(novaSenha) || !vazio(confirmarNovaSenha);
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
        if (urlFoto != null) { // sem arquivo novo, mantém a foto atual
            usuario.setFotoPerfil(urlFoto);
        }

        usuarioRepository.save(usuario);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isEmpty();
    }

    /**
     * ATENÇÃO: regras de negócio abaixo são uma interpretação de RN011/012/019
     * (não tenho o texto delas) — ajuste se divergirem:
     *  - Professor: não exclui se algum curso dele tem aluno matriculado
     *    (CursoService.excluirCurso lança a exceção); os demais cursos são excluídos.
     *  - Aluno: matrículas em aberto são canceladas (vagas devolvidas).
     * Limitação: se o professor tiver vários cursos e um deles bloquear, os
     *    anteriores já foram excluídos (sem transação). Se isso importar, crie
     *    um método "podeExcluir" no CursoService e valide todos antes.
     */
    public void excluirContaDoUsuarioLogado() {
        Usuario usuario = sessaoService.exigirUsuarioLogado();
        String tipo = usuario.getTipoUsuario().name();

        if ("PROFESSOR".equalsIgnoreCase(tipo)) {
            for (Curso curso : cursoRepository.findByProfessorId(usuario.getId())) {
                cursoService.excluirCurso(curso.getId());
            }
        } else if ("ALUNO".equalsIgnoreCase(tipo)) {
            matriculaService.cancelarEmAbertoDoAluno(usuario.getId());
        }

        usuarioRepository.delete(usuario);
        sessaoService.encerrarSessao();
    }
}
