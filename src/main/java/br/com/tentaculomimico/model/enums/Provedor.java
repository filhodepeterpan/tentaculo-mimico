package br.com.tentaculomimico.model.enums;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Provedor {
    LOCAL("local"),
    GOOGLE("google");

    private final String valor;
}
