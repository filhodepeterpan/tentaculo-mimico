package br.com.tentaculomimico.dto;

import java.time.LocalDate;

public record AtualizarPerfilDTO(
        String nome,
        LocalDate dataNascimento,
        String email,
        String fotoPerfil
) {
}