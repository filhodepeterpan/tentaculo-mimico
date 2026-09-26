package br.com.tentaculomimico.controller;

import br.com.tentaculomimico.dto.LoginRequestDTO;
import br.com.tentaculomimico.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<String> login(
        @RequestBody LoginRequestDTO dadosLogin
    ) {
        String token = authService.autenticar(
            dadosLogin.email(),
            dadosLogin.senha()
        );
        return ResponseEntity.ok(token);
    }

    @PostMapping("/cadastro")
    public ResponseEntity cadastrar(
            @RequestParam String nome,
            @RequestParam String email,
            @RequestParam String senha,
            @RequestParam LocalDate dataNascimento,
            @RequestParam(required = false) String tipoUsuario) {

        // pega os campos soltos do formulário e salvar no MongoDB
        br.com.tentaculomimico.dto.CadastroRequestDTO dados =
                new br.com.tentaculomimico.dto.CadastroRequestDTO(nome, email, senha, dataNascimento, tipoUsuario);

        authService.cadastrarUsuario(dados);

        return ResponseEntity.ok("Usuário cadastrado com sucesso!");
    }

    @PostMapping("/esqueci-senha")
    public ResponseEntity<String> esqueciSenha(@RequestParam String email) {
        authService.solicitarRecuperacaoSenha(email);
        return ResponseEntity.ok("um link de recuperação será enviado.");
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<String> redefinirSenha(
        @RequestParam String token,
        @RequestParam String novaSenha,
        @RequestParam String confirmacaoSenha
    ) {
        authService.redefinirSenha(token, novaSenha, confirmacaoSenha);
        return ResponseEntity.ok("Senha atualizada com sucesso!");
    }

    @PostMapping("/login/google")
    public ResponseEntity<String> loginGoogle(
        @RequestBody br.com.tentaculomimico.dto.GoogleLoginRequestDTO dadosGoogle
    ) {
        String token = authService.autenticarComGoogle(
            dadosGoogle.email(),
            dadosGoogle.nome()
        );

        return ResponseEntity.ok(token);
    }

}
