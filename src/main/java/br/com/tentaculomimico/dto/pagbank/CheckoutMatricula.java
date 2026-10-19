package br.com.tentaculomimico.dto.pagbank;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Contratos sanitizados: nunca transportar dados do comprador ou cartão. */
public final class CheckoutMatricula {
    private CheckoutMatricula() {}
    public record Criado(String checkoutId, URI payUrl, Instant expiracao) {}
    public record Consulta(String checkoutId, String referencia, String status, Instant expiracao,
                           List<Pagamento> pagamentos) {}
    public record Pagamento(String id, String status, long centavos, String moeda, String metodo,
                            Instant pagoEm) {}
}
