package br.com.tentaculomimico.dto;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Dados que o Google devolveu no primeiro acesso, guardados na sessão HTTP até a pessoa
 * escolher aluno/professor e informar CPF em /cadastro/google. Enquanto isso nenhuma
 * conta existe no banco.
 *
 * @param dataNascimento vem do claim "birthdate" do Google quando disponível; senão é null
 *                       e a página pede a data.
 */
public record GoogleCadastroPendente(
        String email,
        String nome,
        String googleId,
        LocalDate dataNascimento
) implements Serializable {

    public static final String CHAVE_SESSAO = "googleCadastroPendente";
}
