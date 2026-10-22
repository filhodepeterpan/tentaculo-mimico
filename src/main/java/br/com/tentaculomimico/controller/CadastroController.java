package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.dto.CadastroRequestDTO;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.security.JwtTokenProvider;
import br.com.tentaculomimico.service.CadastroService;
import br.com.tentaculomimico.service.CadastroService.CadastroInvalidoException;
import br.com.tentaculomimico.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Só o POST /cadastro. O GET /cadastro (que renderiza a página) continua
 * onde já está; os dois mapeamentos podem viver em controllers diferentes.
 */
@Controller
@RequiredArgsConstructor
public class CadastroController {

    private final CadastroService cadastroService;
    private final SessaoService sessaoService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/cadastro")
    public String cadastrar(
            @RequestParam(defaultValue = "") String nome,
            @RequestParam(defaultValue = "") String email,
            @RequestParam(defaultValue = "") String cpf,
            // os nomes com hífen vêm dos ids dos inputs (inputs.html usa o id como name)
            @RequestParam(name = "data-nascimento", defaultValue = "") String dataNascimento,
            @RequestParam(defaultValue = "aluno") String tipoUsuario,
            @RequestParam(defaultValue = "") String senha,
            @RequestParam(name = "confirmar-senha", defaultValue = "") String confirmarSenha,
            HttpServletResponse response,
            Model model
    ) {
        CadastroRequestDTO dto = new CadastroRequestDTO(
                nome, email, cpf, dataNascimento, tipoUsuario, senha, confirmarSenha);

        try {
            Usuario usuario = cadastroService.cadastrar(dto);

            // Mesmo comportamento do login: sessão + cookie JWT e vai para /cursos.
            sessaoService.iniciarSessao(usuario);
            String token = jwtTokenProvider.gerarToken(usuario.getEmail(), usuario.getTipoUsuario().toString());
            Cookie cookie = new Cookie("jwt", token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            return "redirect:/cursos";
        } catch (CadastroInvalidoException e) {
            for (Map.Entry<String, String> erro : e.getErros().entrySet()) {
                String chave = erro.getKey();
                model.addAttribute("erro" + Character.toUpperCase(chave.charAt(0)) + chave.substring(1), erro.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("erroGeral", "Não foi possível concluir o cadastro. Tente novamente.");
        }

        // Reexibe o formulário com o que a pessoa digitou (nunca devolvemos a senha).
        model.addAttribute("nomePreenchido", nome);
        model.addAttribute("emailPreenchido", email);
        model.addAttribute("cpfPreenchido", cpf);
        model.addAttribute("dataNascimentoPreenchida", dataNascimento);
        model.addAttribute("tipoUsuarioSelecionado", tipoUsuario);
        return "cadastro";
    }
}
