package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.client.PagBankClient;
import br.com.tentaculomimico.service.PagamentoCursoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Recebe as notificações do PagBank. A rota é pública e sem CSRF (ver SecurityConfig), então este
 * controller não confia em nada do corpo: o service confere o pedido na API do PagBank antes de agir.
 * Resposta 200 = recebido; erro = o PagBank tenta reenviar depois.
 */
@Controller
public class PagBankWebhookController {
    private static final int TAMANHO_MAXIMO = 200_000;

    private final PagamentoCursoService pagamentoCursoService;

    public PagBankWebhookController(PagamentoCursoService pagamentoCursoService) {
        this.pagamentoCursoService = pagamentoCursoService;
    }

    @PostMapping("/webhooks/pagbank")
    public ResponseEntity<Void> receber(@RequestBody String corpo) {
        if (corpo == null || corpo.isBlank() || corpo.length() > TAMANHO_MAXIMO) {
            return ResponseEntity.badRequest().build();
        }
        try {
            pagamentoCursoService.processarNotificacao(corpo);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (PagBankClient.ConsultaIndisponivelException e) {
            return ResponseEntity.status(503).build();
        }
    }
}
