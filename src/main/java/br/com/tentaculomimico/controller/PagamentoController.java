package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.service.MatriculaService;
import br.com.tentaculomimico.service.SessaoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

/**
 * Tela de pagamento da matrícula (Pix, cartão de crédito e cartão de débito).
 *
 * ATENÇÃO, PROVISÓRIO: não há gateway de pagamento. O botão da tela só avisa o servidor que "pagou"
 * e o servidor acredita. Por isso o POST só funciona com app.pagamento.simulado=true (padrão, para
 * desenvolvimento). Em produção defina app.pagamento.simulado=false e troque a confirmação por um
 * webhook do gateway chamando MatriculaService.confirmarPagamento.
 *
 * Os dados do cartão NUNCA chegam aqui: os campos da tela ficam fora de qualquer <form> e só o método
 * escolhido (pix/credito/debito) é enviado. Quando houver gateway, a tokenização do cartão deve ser
 * feita no navegador pelo SDK dele.
 */
@Controller
public class PagamentoController {

    private static final Set<String> METODOS = Set.of("pix", "credito", "debito");

    private final MatriculaService matriculaService;
    private final CursoRepository cursoRepository;
    private final SessaoService sessaoService;
    private final boolean pagamentoSimulado;

    public PagamentoController(MatriculaService matriculaService,
                               CursoRepository cursoRepository,
                               SessaoService sessaoService,
                               @Value("${app.pagamento.simulado:true}") boolean pagamentoSimulado) {
        this.matriculaService = matriculaService;
        this.cursoRepository = cursoRepository;
        this.sessaoService = sessaoService;
        this.pagamentoSimulado = pagamentoSimulado;
    }

    @GetMapping("/pagamentos/{matriculaId}")
    public String pagina(@PathVariable String matriculaId, Model model) {
        if (sessaoService.usuarioLogado() == null) {
            return "redirect:/login";
        }

        Matricula matricula;
        Curso curso;
        try {
            matricula = matriculaService.buscarMatriculaDoAlunoLogado(matriculaId);
            curso = cursoRepository.findById(matricula.getCursoId()).orElse(null);
        } catch (RuntimeException e) {
            return "redirect:/cursos"; // não existe ou não é dele
        }
        if (curso == null) {
            return "redirect:/cursos";
        }

        StatusMatricula status = matricula.getStatusMatricula();
        boolean concluido = status == StatusMatricula.ATIVA;
        if (!concluido && status != StatusMatricula.AGUARDANDO_PAGAMENTO) {
            return "redirect:/cursos/" + curso.getId(); // nada a pagar (pendente, recusada, cancelada)
        }

        model.addAttribute("matriculaId", matricula.getId());
        model.addAttribute("curso", curso);
        // Dados genéricos que a tela de pagamento usa (a mesma tela serve para doação).
        model.addAttribute("ehDoacao", false);
        model.addAttribute("tituloPagina", "Pagamento da matrícula");
        model.addAttribute("itemNome", curso.getNome());
        model.addAttribute("rotuloValor", "Mensalidade");
        model.addAttribute("valorTexto", "R$ " + curso.getPreco());
        model.addAttribute("voltarUrl", "/cursos/" + curso.getId());
        model.addAttribute("voltarTexto", "Voltar ao curso");
        model.addAttribute("urlPagina", "/pagamentos/" + matricula.getId());
        model.addAttribute("acaoConfirmar", "/pagamentos/" + matricula.getId() + "/confirmar");
        model.addAttribute("pagamentoConcluido", concluido);
        model.addAttribute("pagamentoSimulado", pagamentoSimulado);
        // TODO(gateway): trocar pelo "Pix copia e cola" real devolvido pelo gateway. Este texto NÃO é um
        // Pix válido de propósito: se alguém escanear, o banco recusa em vez de pagar a pessoa errada.
        model.addAttribute("pixCopiaECola", "PIX-SIMULADO-" + matricula.getId());
        return "pagamento";
    }

    @PostMapping("/pagamentos/{matriculaId}/confirmar")
    public String confirmar(@PathVariable String matriculaId,
                            @RequestParam(defaultValue = "") String metodo,
                            RedirectAttributes redirect) {
        if (sessaoService.usuarioLogado() == null) {
            return "redirect:/login";
        }
        if (!pagamentoSimulado) {
            redirect.addFlashAttribute("erroPagamento", "O pagamento online ainda não está disponível.");
            return "redirect:/pagamentos/" + matriculaId;
        }
        if (!METODOS.contains(metodo)) {
            redirect.addFlashAttribute("erroPagamento", "Escolha uma forma de pagamento.");
            return "redirect:/pagamentos/" + matriculaId;
        }

        try {
            matriculaService.confirmarPagamento(matriculaId);
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erroPagamento", e.getMessage());
        }
        // A página decide: ATIVA mostra a confirmação; qualquer outro estado redireciona sozinho.
        return "redirect:/pagamentos/" + matriculaId;
    }
}