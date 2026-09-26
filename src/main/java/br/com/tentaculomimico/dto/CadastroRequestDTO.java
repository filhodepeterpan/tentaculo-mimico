package br.com.tentaculomimico.dto;

import java.time.LocalDate;

public record CadastroRequestDTO(String nome, String email, String senha, LocalDate dataNascimento, String tipoUsuario) {
}