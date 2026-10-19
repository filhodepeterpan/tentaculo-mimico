package br.com.tentaculomimico.client;

import br.com.tentaculomimico.dto.pagbank.CheckoutMatricula.Consulta;

public interface ConsultaCheckoutPagBank {
    Consulta consultar(String checkoutId);
}
