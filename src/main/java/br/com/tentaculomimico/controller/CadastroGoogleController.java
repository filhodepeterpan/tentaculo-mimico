package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.dto.GoogleCadastroPendente;
import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.AuthService;
import br.com.tentaculomimico.service.CadastroService;
import br.com.tentaculomimico.service.CadastroService.CadastroInvalidoException;
import br.com.tentaculomimico.service.SessaoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Segunda etapa do cadastro com Google (a primeira é o OAuth2LoginSucessoHandler).
 * A conta só existe depois do POST daqui: até lá os dados do Google ficam só na sessão.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class CadastroGoogleController {

    private final CadastroService cadastroService;
    private final AuthService authService;
    private final SessaoService sessaoService;

    @GetMapping("/cadastro/google")
    public String pagina(HttpSession session, Model model) {
        GoogleCadastroPendente pendente = pendenteDaSessao(session);
        if (pendente == null) {
            return "redirect:/login";
        }
        preencherPagina(model, pendente, "aluno", "", "");
        return "cadastro-google";
    }

    @PostMapping("/cadastro/google")
    public String concluir(
            @RequestParam(defaultValue = "") String tipoUsuario,
            @RequestParam(defaultValue = "") String cpf,
            // o nome com hífen vem do id do input (inputs.html usa o id como name)
            @RequestParam(name = "data-nascimento", defaultValue = "") String dataNascimento,
            HttpSession session,
            HttpServletResponse response,
            Model model
    ) {
        GoogleCadastroPendente pendente = pendenteDaSessao(session);
        if (pendente == null) {
            return "redirect:/login";
        }

        try {
            Usuario usuario = cadastroService.cadastrarComGoogle(pendente, tipoUsuario, cpf, dataNascimento);

            session.removeAttribute(GoogleCadastroPendente.CHAVE_SESSAO);
            sessaoService.iniciarSessao(usuario);

            Cookie cookie = new Cookie("jwt", authService.gerarToken(usuario));
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);

            return "redirect:/cursos";
        } catch (CadastroInvalidoException e) {
            for (Map.Entry<String, String> erro : e.getErros().entrySet()) {
                String chave = erro.getKey();
                if ("email".equals(chave)) {
                    // Não há campo de e-mail nesta página: aparece como alerta geral.
                    model.addAttribute("erroGeral", erro.getValue());
                } else {
                    model.addAttribute("erro" + Character.toUpperCase(chave.charAt(0)) + chave.substring(1), erro.getValue());
                }
            }
        } catch (Exception e) {
            log.error("Erro inesperado ao concluir o cadastro com Google", e);
            model.addAttribute("erroGeral", "Não foi possível concluir o cadastro. Tente novamente.");
        }

        preencherPagina(model, pendente, tipoUsuario, cpf, dataNascimento);
        return "cadastro-google";
    }

    private GoogleCadastroPendente pendenteDaSessao(HttpSession session) {
        Object valor = session.getAttribute(GoogleCadastroPendente.CHAVE_SESSAO);
        return valor instanceof GoogleCadastroPendente p ? p : null;
    }

    private void preencherPagina(Model model, GoogleCadastroPendente pendente,
                                 String tipoUsuario, String cpf, String dataNascimento) {
        model.addAttribute("emailGoogle", pendente.email());
        model.addAttribute("nomeGoogle", pendente.nome());
        // Se o Google já mandou a data, a página não pede de novo.
        model.addAttribute("pedirDataNascimento", pendente.dataNascimento() == null);
        model.addAttribute("tipoUsuarioSelecionado", tipoUsuario);
        model.addAttribute("cpfPreenchido", cpf);
        model.addAttribute("dataNascimentoPreenchida", dataNascimento);
    }
}
