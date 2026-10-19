package br.com.tentaculomimico.client;

import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.Criado;

public interface CheckoutMatriculaGateway {
    Criado criar(String referenceId, String nome, int valorCentavos, String retornoUrl,
                 String paymentNotificationUrl, String checkoutNotificationUrl);
}
