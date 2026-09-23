package br.com.tentaculomimico.model.enums;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TipoUsuario {
    ALUNO("aluno"),
    PROFESSOR("professor"),
    ADMINISTRADOR("administrador");

    private final String valor;
}
