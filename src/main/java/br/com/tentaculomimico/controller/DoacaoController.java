package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.service.DoacaoService;
import br.com.tentaculomimico.service.PagBankClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

@Controller
public class DoacaoController {
    private final DoacaoService doacaoService;

    public DoacaoController(DoacaoService doacaoService) {
        this.doacaoService = doacaoService;
    }

    @PostMapping("/doacoes")
    public ResponseEntity<Void> iniciar(@RequestParam(name = "valor", defaultValue = "") String valor,
                                        @RequestParam(name = "mensagem", defaultValue = "") String mensagem,
                                        RedirectAttributes redirect) {
        try {
            URI pay = doacaoService.iniciar(valor, mensagem);
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(pay).build();
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("erroDoacao", e.getMessage());
        } catch (PagBankClient.CheckoutIndisponivelException e) {
            redirect.addFlashAttribute("erroDoacao", "Não foi possível abrir o PagBank agora. Se uma página de pagamento já foi aberta, confira-a antes de tentar novamente.");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, "/doacoes").build();
    }

    @GetMapping("/pagamentos/doacao")
    public String paginaLegada() {
        return "redirect:/doacoes";
    }
}
