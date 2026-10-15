package br.com.tentaculomimico.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErroController implements ErrorController {

    @RequestMapping("/error")
    public String tratarErro(HttpServletRequest request, HttpServletResponse response, Model model) {
        Object atributoStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = (atributoStatus instanceof Integer) ? (Integer) atributoStatus : 500;

        String titulo;
        String mensagem;

        switch (status) {
            case 400:
                titulo = "Requisição inválida";
                mensagem = "Não foi possível entender o que foi enviado. Confira os dados e tente novamente.";
                break;
            case 401:
                titulo = "Você precisa entrar na sua conta";
                mensagem = "Faça login para acessar esta página.";
                break;
            case 403:
                titulo = "Você não tem acesso a esta página";
                mensagem = "Verifique se você está na conta certa, ou volte para a página inicial.";
                break;
            case 404:
                titulo = "Página não encontrada";
                mensagem = "O endereço que você tentou acessar não existe ou foi movido.";
                break;
            case 405:
                titulo = "Ação não permitida";
                mensagem = "Esta página não aceita o tipo de ação que foi tentada.";
                break;
            default:
                if (status >= 500) {
                    titulo = "Algo deu errado do nosso lado";
                    mensagem = "Tivemos um problema inesperado. Tente novamente em alguns instantes.";
                } else {
                    titulo = "Ocorreu um erro";
                    mensagem = "Não foi possível concluir o que você pediu. Tente novamente.";
                }
        }

        response.setStatus(status);

        model.addAttribute("status", status);
        model.addAttribute("titulo", titulo);
        model.addAttribute("mensagem", mensagem);

        return "error";
    }
}
