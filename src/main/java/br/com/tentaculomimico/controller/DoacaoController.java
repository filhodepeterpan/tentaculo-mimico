package br.com.tentaculomimico.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Doação: a página /doacoes manda o valor (e a mensagem) para cá, e daqui o doador segue para a
 * mesma tela de pagamento usada na matrícula (Pix, crédito, débito).
 *
 * Não exige login (/doacoes e /pagamentos/** são públicos). A doação pendente fica na sessão HTTP.
 *
 * PROVISÓRIO (igual ao pagamento da matrícula): sem gateway, e nada é gravado. TODO(back):
 *  - criar o gateway/cobrança real e o registro da doação (valor, mensagem, usuário se logado, status);
 *  - trocar a confirmação simulada por webhook do gateway.
 * Só funciona com app.pagamento.simulado=true.
 */
@Controller
public class DoacaoController {

    private static final String SESSAO_DOACAO = "doacaoPendente";
    private static final Set<String> METODOS = Set.of("pix", "credito", "debito");
    private static final BigDecimal MINIMO = new BigDecimal("1.00");
    private static final BigDecimal MAXIMO = new BigDecimal("10000.00");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final boolean pagamentoSimulado;

    public DoacaoController(@Value("${app.pagamento.simulado:true}") boolean pagamentoSimulado) {
        this.pagamentoSimulado = pagamentoSimulado;
    }

    /** Doação aguardando pagamento (guardada na sessão; por isso Serializable). */
    public record DoacaoPendente(BigDecimal valor, String mensagem, String codigoPix) implements Serializable {}

    @PostMapping("/doacoes")
    public String iniciar(@RequestParam(name = "valor", defaultValue = "") String valorInformado,
                          @RequestParam(name = "mensagem", defaultValue = "") String mensagem,
                          HttpSession sessao,
                          RedirectAttributes redirect) {
        BigDecimal valor = lerValor(valorInformado);
        if (valor == null || valor.compareTo(MINIMO) < 0 || valor.compareTo(MAXIMO) > 0) {
            redirect.addFlashAttribute("erroDoacao", "Informe um valor entre R$ 1,00 e R$ 10.000,00.");
            return "redirect:/doacoes";
        }
        String msg = mensagem.strip();
        if (msg.length() > 500) msg = msg.substring(0, 500);

        sessao.setAttribute(SESSAO_DOACAO, new DoacaoPendente(valor, msg, "PIX-SIMULADO-DOACAO-" + UUID.randomUUID()));
        return "redirect:/pagamentos/doacao";
    }

    @GetMapping("/pagamentos/doacao")
    public String pagina(HttpSession sessao, Model model) {
        // Vindo do POST de confirmação, a doação já foi removida da sessão e o flash traz o valor.
        boolean concluida = Boolean.TRUE.equals(model.asMap().get("doacaoConcluida"));
        DoacaoPendente doacao = (DoacaoPendente) sessao.getAttribute(SESSAO_DOACAO);
        if (!concluida && doacao == null) {
            return "redirect:/doacoes";
        }

        model.addAttribute("ehDoacao", true);
        model.addAttribute("pagamentoConcluido", concluida);
        model.addAttribute("pagamentoSimulado", pagamentoSimulado);
        model.addAttribute("tituloPagina", "Pagamento da doação");
        model.addAttribute("itemNome", "Doação");
        model.addAttribute("rotuloValor", "Valor");
        model.addAttribute("voltarUrl", "/doacoes");
        model.addAttribute("voltarTexto", "Voltar às doações");
        model.addAttribute("urlPagina", "/pagamentos/doacao");
        model.addAttribute("acaoConfirmar", "/pagamentos/doacao/confirmar");
        if (doacao != null) {
            model.addAttribute("valorTexto", formatar(doacao.valor()));
            // TODO(gateway): trocar pelo "Pix copia e cola" real. Este texto NÃO é um Pix válido de propósito.
            model.addAttribute("pixCopiaECola", doacao.codigoPix());
        }
        return "pagamento";
    }

    @PostMapping("/pagamentos/doacao/confirmar")
    public String confirmar(@RequestParam(defaultValue = "") String metodo,
                            HttpSession sessao,
                            RedirectAttributes redirect) {
        DoacaoPendente doacao = (DoacaoPendente) sessao.getAttribute(SESSAO_DOACAO);
        if (doacao == null) {
            return "redirect:/doacoes";
        }
        if (!pagamentoSimulado) {
            redirect.addFlashAttribute("erroPagamento", "O pagamento online ainda não está disponível.");
            return "redirect:/pagamentos/doacao";
        }
        if (!METODOS.contains(metodo)) {
            redirect.addFlashAttribute("erroPagamento", "Escolha uma forma de pagamento.");
            return "redirect:/pagamentos/doacao";
        }

        // TODO(back): gravar a doação aqui (ou, com gateway, no webhook de pagamento confirmado).
        sessao.removeAttribute(SESSAO_DOACAO);
        redirect.addFlashAttribute("doacaoConcluida", true);
        redirect.addFlashAttribute("valorTexto", formatar(doacao.valor()));
        return "redirect:/pagamentos/doacao";
    }

    /** Aceita "10", "10,5", "R$ 1.234,56", "12.50". Devolve null se não for um número válido. */
    static BigDecimal lerValor(String texto) {
        if (texto == null) return null;
        String t = texto.replace("R$", "").replaceAll("\\s", "");
        if (t.isEmpty()) return null;
        if (t.contains(",")) {
            t = t.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(t).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatar(BigDecimal valor) {
        return String.format(PT_BR, "R$ %,.2f", valor);
    }
}
