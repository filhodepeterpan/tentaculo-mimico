package br.com.tentaculomimico.dto.pagbank;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

public record CriarCheckout(@JsonProperty("reference_id") String referenceId,
                           @JsonProperty("expiration_date") OffsetDateTime expirationDate,
                           @JsonProperty("customer_modifiable") boolean customerModifiable,
                           List<Item> items,
                           @JsonProperty("redirect_url") String redirectUrl,
                           @JsonProperty("return_url") String returnUrl,
                           @JsonProperty("payment_methods_configs") List<MetodoConfig> paymentMethodsConfigs) {
    public record Item(String name, int quantity, @JsonProperty("unit_amount") int unitAmount) {}
    public record MetodoConfig(String type, @JsonProperty("config_options") List<Opcao> configOptions) {}
    public record Opcao(String option, String value) {}
}
