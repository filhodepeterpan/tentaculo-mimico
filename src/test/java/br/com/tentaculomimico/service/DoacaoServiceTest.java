package br.com.tentaculomimico.service;

import br.com.tentaculomimico.config.PagBankProperties;
import br.com.tentaculomimico.dto.pagbank.CheckoutRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DoacaoServiceTest {
    private final PagBankClient client = mock(PagBankClient.class);
    private final DoacaoService service = new DoacaoService(client,
            new PagBankProperties("token", PagBankProperties.Environment.sandbox, "https://exemplo.org"));

    @Test
    void converteCentavosExatosSemPersistirMensagem() {
        when(client.criar(any())).thenReturn(URI.create("https://pagamento.pagbank.com.br/pagamento?code=1"));
        service.iniciar("R$ 1.234,56", "Mensagem pessoal");
        var pedido = org.mockito.ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(client).criar(pedido.capture());
        assertEquals(123456, pedido.getValue().items().getFirst().unitAmount());
        assertEquals("https://exemplo.org/doacoes?retorno=pagbank", pedido.getValue().redirectUrl());
        assertEquals(pedido.getValue().redirectUrl(), pedido.getValue().returnUrl());
        assertEquals(1, pedido.getValue().paymentMethodsConfigs().size());
    }

    @Test
    void rejeitaValorInvalidoAntesDaChamada() {
        for (String valor : new String[]{"", "0", "-2", "10000.01", "1.234", "1,001", "1e2", "abc"}) {
            assertThrows(IllegalArgumentException.class, () -> service.iniciar(valor, ""), valor);
        }
        assertThrows(IllegalArgumentException.class, () -> service.iniciar("10", "x".repeat(501)));
        verifyNoInteractions(client);
    }

    @Test
    void rejeitaOrigemPublicaInsegura() {
        DoacaoService inseguro = new DoacaoService(client,
                new PagBankProperties("token", PagBankProperties.Environment.sandbox, "http://exemplo.org"));
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> inseguro.iniciar("10", ""));
        DoacaoService producaoLocal = new DoacaoService(client,
                new PagBankProperties("token", PagBankProperties.Environment.production, "http://localhost:8080"));
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> producaoLocal.iniciar("10", ""));
        DoacaoService comCredenciaisNaUrl = new DoacaoService(client,
                new PagBankProperties("token", PagBankProperties.Environment.sandbox, "http://usuario@localhost:8080"));
        assertThrows(PagBankClient.CheckoutIndisponivelException.class, () -> comCredenciaisNaUrl.iniciar("10", ""));
        verifyNoInteractions(client);
    }

    @Test
    void permiteRetornoLocalSomenteNoSandbox() {
        when(client.criar(any())).thenReturn(URI.create("https://pagamento.pagbank.com.br/pagamento?code=1"));
        DoacaoService local = new DoacaoService(client,
                new PagBankProperties("token", PagBankProperties.Environment.sandbox, "http://localhost:8080"));
        local.iniciar("10", "");
        var pedido = org.mockito.ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(client).criar(pedido.capture());
        assertEquals("http://localhost:8080/doacoes?retorno=pagbank", pedido.getValue().redirectUrl());
        assertEquals(pedido.getValue().redirectUrl(), pedido.getValue().returnUrl());
    }

    @Test
    void aceitaLimitesIncluindoCentavoExato() {
        when(client.criar(any())).thenReturn(URI.create("https://pagamento.pagbank.com.br/pagamento?code=1"));
        service.iniciar("1,00", "");
        service.iniciar("10.000,00", "");
        var pedido = org.mockito.ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(client, times(2)).criar(pedido.capture());
        assertEquals(100, pedido.getAllValues().get(0).items().getFirst().unitAmount());
        assertEquals(1_000_000, pedido.getAllValues().get(1).items().getFirst().unitAmount());
    }
}
