package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.model.Curso;
import br.com.tentaculomimico.model.Matricula;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.service.CheckoutCursoService;
import br.com.tentaculomimico.service.MatriculaService;
import br.com.tentaculomimico.service.PagamentoCursoService;
import br.com.tentaculomimico.service.SessaoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;

/**
 * Pagamento da matrícula pelo checkout do PagBank (Pix, crédito com parcelamento e débito).
 *
 *  - GET /pagamentos/{id}         : o botão "Pagar". Abre (ou reaproveita) o checkout e redireciona o aluno
 *                                   para o PagBank. Não mostra tela própria.
 *  - GET /pagamentos/{id}/retorno : para onde o PagBank devolve o aluno. Confere o pagamento e mostra o resultado.
 *
 * Quem ativa a matrícula é a confirmação do PagBank (webhook ou conferência no retorno, ver
 * PagamentoCursoService). Nenhum clique do aluno, e nenhum dado vindo do navegador, ativa a matrícula.
 */
@Controller
public class PagamentoController {

    private final MatriculaService matriculaService;
    private final CursoRepository cursoRepository;
    private final SessaoService sessaoService;
    private final CheckoutCursoService checkoutCursoService;
    private final PagamentoCursoService pagamentoCursoService;

    public PagamentoController(MatriculaService matriculaService,
                               CursoRepository cursoRepository,
                               SessaoService sessaoService,
                               CheckoutCursoService checkoutCursoService,
                               PagamentoCursoService pagamentoCursoService) {
        this.matriculaService = matriculaService;
        this.cursoRepository = cursoRepository;
        this.sessaoService = sessaoService;
        this.checkoutCursoService = checkoutCursoService;
        this.pagamentoCursoService = pagamentoCursoService;
    }

    @GetMapping("/pagamentos/{matriculaId}")
    public Object pagar(@PathVariable String matriculaId, RedirectAttributes redirect) {
        if (sessaoService.usuarioLogado() == null) {
            return "redirect:/login";
        }

        Matricula matricula;
        try {
            matricula = matriculaService.buscarMatriculaDoAlunoLogado(matriculaId);
        } catch (RuntimeException e) {
            return "redirect:/cursos"; // não existe ou não é dele
        }
        String paginaDoCurso = "redirect:/cursos/" + matricula.getCursoId();

        // Antes de abrir outro checkout, confere se o anterior já foi pago (evita cobrar duas vezes
        // quem pagou e voltou sem passar pela tela de retorno).
        if (matricula.getStatusMatricula() == StatusMatricula.AGUARDANDO_PAGAMENTO) {
            pagamentoCursoService.reconciliar(matriculaId);
            matricula = matriculaService.buscarMatriculaDoAlunoLogado(matriculaId);
        }

        StatusMatricula status = matricula.getStatusMatricula();
        if (status == StatusMatricula.ATIVA) {
            return "redirect:/pagamentos/" + matriculaId + "/retorno";
        }
        if (status != StatusMatricula.AGUARDANDO_PAGAMENTO) {
            return paginaDoCurso; // nada a pagar (pendente, recusada, cancelada)
        }

        try {
            URI destino = checkoutCursoService.iniciar(matriculaId);
            RedirectView view = new RedirectView(destino.toString());
            view.setExposeModelAttributes(false);
            return view;
        } catch (PagBankClient.CheckoutIndisponivelException e) {
            redirect.addFlashAttribute("erroPagamento", "Não foi possível abrir o PagBank agora. Tente novamente em instantes.");
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("erroPagamento", e.getMessage());
        }
        return paginaDoCurso;
    }

    @GetMapping("/pagamentos/{matriculaId}/retorno")
    public String retorno(@PathVariable String matriculaId, Model model) {
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

        if (matricula.getStatusMatricula() == StatusMatricula.AGUARDANDO_PAGAMENTO) {
            pagamentoCursoService.reconciliar(matriculaId);
            matricula = matriculaService.buscarMatriculaDoAlunoLogado(matriculaId);
        }

        StatusMatricula status = matricula.getStatusMatricula();
        boolean concluido = status == StatusMatricula.ATIVA;
        if (!concluido && status != StatusMatricula.AGUARDANDO_PAGAMENTO) {
            return "redirect:/cursos/" + curso.getId();
        }

        model.addAttribute("matriculaId", matricula.getId());
        model.addAttribute("curso", curso);
        model.addAttribute("pagamentoConcluido", concluido);
        return "pagamento";
    }
}
