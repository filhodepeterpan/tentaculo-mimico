package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.ConfirmacaoPagamentoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookMatriculaController {
    public static final String ROTA = "/webhooks/pagbank/matriculas";
    private final ConfirmacaoPagamentoService service;
    public WebhookMatriculaController(ConfirmacaoPagamentoService service) { this.service = service; }
    @PostMapping(ROTA)
    public ResponseEntity<Void> receber(@RequestBody byte[] corpo,
            @RequestHeader(value = "x-authenticity-token", required = false) String assinatura) {
        try {
            service.processarNotificacao(corpo, assinatura);
            return ResponseEntity.noContent().build();
        } catch (ConfirmacaoPagamentoService.AssinaturaInvalida e) {
            return ResponseEntity.status(401).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).build();
        }
    }
}
