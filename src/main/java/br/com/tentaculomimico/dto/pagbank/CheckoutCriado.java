package br.com.tentaculomimico.dto.pagbank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutCriado(List<Link> links) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Link(String rel, String href) {}
}
