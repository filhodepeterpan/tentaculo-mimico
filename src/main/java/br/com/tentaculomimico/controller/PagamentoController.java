package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.client.PagBankMatriculaClient;
import br.com.tentaculomimico.model.StatusMatricula;
import br.com.tentaculomimico.repository.CursoRepository;
import br.com.tentaculomimico.repository.PagamentoMatriculaRepository;
import br.com.tentaculomimico.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.net.URI;

@Controller
public class PagamentoController {
    private static final Logger LOG = LoggerFactory.getLogger(PagamentoController.class);
    private final MatriculaService matriculas;
    private final CursoRepository cursos;
    private final SessaoService sessao;
    private final PagamentoMatriculaService pagamentos;
    private final PagamentoMatriculaRepository registros;
    public PagamentoController(MatriculaService matriculas, CursoRepository cursos, SessaoService sessao,
            PagamentoMatriculaService pagamentos, PagamentoMatriculaRepository registros) {
        this.matriculas = matriculas; this.cursos = cursos; this.sessao = sessao;
        this.pagamentos = pagamentos; this.registros = registros;
    }
    @GetMapping("/pagamentos/{matriculaId}")
    public String pagina(@PathVariable String matriculaId, Model model) {
        if (sessao.usuarioLogado() == null) return "redirect:/login";
        try {
            var m = matriculas.buscarMatriculaDoAlunoLogado(matriculaId);
            var curso = cursos.findById(m.getCursoId()).orElseThrow();
            model.addAttribute("curso", curso);
            model.addAttribute("matriculaId", matriculaId);
            model.addAttribute("pagamentoConcluido", m.getStatusMatricula() == StatusMatricula.ATIVA);
            model.addAttribute("podePagar", m.getStatusMatricula() == StatusMatricula.AGUARDANDO_PAGAMENTO);
            model.addAttribute("estadoPagamento", registros.findByMatriculaId(matriculaId)
                    .map(p -> switch (p.getTentativas().getLast().getStatus()) {
                        case CRIANDO -> "Preparando Checkout";
                        case INCERTA -> "Aguardando conciliação da tentativa anterior";
                        case WAITING -> "Aguardando pagamento";
                        case IN_ANALYSIS -> "Pagamento em análise";
                        case PAID -> "Pagamento confirmado";
                        case DECLINED -> "Pagamento recusado; retome o Checkout para tentar novamente";
                        case CANCELED -> "Pagamento cancelado";
                        case EXPIRED -> "Checkout expirado; nova tentativa disponível";
                    }).orElse("Não iniciado"));
            return "pagamento";
        } catch (RuntimeException e) { return "redirect:/cursos"; }
    }
    @PostMapping("/pagamentos/{matriculaId}/iniciar")
    public ResponseEntity<Void> iniciar(@PathVariable String matriculaId, RedirectAttributes redirect) {
        String matriculaLogId = safeId(matriculaId);
        LOG.info("POST /pagamentos/{matriculaId}/iniciar reached controller: matriculaId={}", matriculaLogId);
        if (sessao.usuarioLogado() == null) {
            LOG.warn("Matrícula payment initiation has no logged-in user: matriculaId={}", matriculaLogId);
            return ResponseEntity.status(303).location(URI.create("/login")).build();
        }
        try {
            URI pay = PagBankMatriculaClient.validarPay(pagamentos.iniciar(matriculaId).toString());
            LOG.info("Matrícula payment will return HTTP 303 to PagBank Sandbox: matriculaId={}, host={}",
                    matriculaLogId, pay.getHost());
            return ResponseEntity.status(303).location(pay).build();
        } catch (RuntimeException e) {
            LOG.warn("Matrícula payment will not redirect to PagBank; returning HTTP 303 to local payment page: matriculaId={}, exceptionType={}",
                    matriculaLogId, e.getClass().getSimpleName());
            redirect.addFlashAttribute("erroPagamento", "Não foi possível iniciar o pagamento. Aguarde a conciliação ou atualize a página antes de tentar novamente.");
            return ResponseEntity.status(303).location(URI.create("/pagamentos/" + matriculaId)).build();
        }
    }

    private static String safeId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,100}") ? id : "invalid";
    }
}
