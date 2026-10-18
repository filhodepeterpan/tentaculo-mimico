package br.com.tentaculomimico.service;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CriarCheckout;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class DoacaoService {
    private final PagBankClient client;
    private final PagBankProperties properties;

    public DoacaoService(PagBankClient client, PagBankProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public URI iniciar(String valorInformado, String mensagem) {
        BigDecimal valor = lerValor(valorInformado);
        if (valor == null || valor.compareTo(BigDecimal.ONE) < 0 || valor.compareTo(new BigDecimal("10000")) > 0) {
            throw new IllegalArgumentException("Informe um valor entre R$ 1,00 e R$ 10.000,00, com até duas casas decimais.");
        }
        // Compatibilidade com formulários antigos; a mensagem não é enviada, guardada nem registrada.
        if (mensagem != null && mensagem.length() > 500) {
            throw new IllegalArgumentException("A mensagem deve ter no máximo 500 caracteres.");
        }
        try {
            String retorno = properties.retorno().toString();
            CriarCheckout checkout = new CriarCheckout(UUID.randomUUID().toString(),
                    OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), true,
                    List.of(new CriarCheckout.Item("Doação ao Tentáculo Mímico", 1, valor.movePointRight(2).intValueExact())),
                    retorno, retorno,
                    List.of(new CriarCheckout.MetodoConfig("CREDIT_CARD",
                            List.of(new CriarCheckout.Opcao("INSTALLMENTS_LIMIT", "1")))));
            return client.criar(checkout);
        } catch (IllegalStateException e) {
            throw new PagBankClient.CheckoutIndisponivelException();
        }
    }

    static BigDecimal lerValor(String texto) {
        if (texto == null) return null;
        String t = texto.strip().replace("R$", "").replaceAll("\\s", "");
        if (t.length() > 20) return null;
        if (t.matches("\\d{1,3}(\\.\\d{3})+,\\d{1,2}")) t = t.replace(".", "").replace(',', '.');
        else if (t.matches("\\d+,\\d{1,2}")) t = t.replace(',', '.');
        else if (!t.matches("\\d+(\\.\\d{1,2})?")) return null;
        return new BigDecimal(t);
    }
}
