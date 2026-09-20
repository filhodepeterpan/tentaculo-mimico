package br.com.tentaculomimico.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void enviarEmailRecuperacao(String destinatario, String token) {
        SimpleMailMessage mensagem = new SimpleMailMessage();

        mensagem.setTo(destinatario);
        mensagem.setSubject("Recuperação de Palavra-passe - Tentáculo Mímico");

        String link = "http://localhost:8080/nova-senha?token=" + token;

        mensagem.setText("Olá!\n\n" +
                "Se esqueceu a senha, click em REDEFINIR SENHA.\n" +
                "Clica no link abaixo para criar uma nova:\n\n" +
                link + "\n\n" +
                "Se não foi você que solicitou isso, pode ignorar este e-mail com segurança.");

        mailSender.send(mensagem);
    }
}