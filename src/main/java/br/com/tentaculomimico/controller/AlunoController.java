package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.model.Usuario;
import br.com.tentaculomimico.service.AlunoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/alunos")
@RequiredArgsConstructor
public class AlunoController {

    private final AlunoService alunoService;

    @GetMapping("/perfil")
    public ResponseEntity verPerfil(Principal principal) {
        // Pega o e-mail do usuário logado através do token JWT
        String email = principal.getName();
        Usuario usuario = alunoService.buscarPerfilPorEmail(email);

        return ResponseEntity.ok(usuario);
    }
    @DeleteMapping("/conta")
    public ResponseEntity excluirConta(Principal principal) {
        String email = principal.getName();
        alunoService.excluirConta(email);

        return ResponseEntity.ok("Conta excluída");
    }
}