package br.com.tentaculomimico.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // application.properties: app.base-url=https://seu-dominio.com  (padrão: ambiente local)
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void enviarEmailRecuperacao(String destinatario, String token) {
        SimpleMailMessage mensagem = new SimpleMailMessage();

        mensagem.setTo(destinatario);
        mensagem.setSubject("Recuperação de Senha");

        String link = baseUrl + "/nova-senha?token=" + token;

        mensagem.setText("Olá!\n\n" +
                "Recebemos um pedido para redefinir a sua senha.\n" +
                "Clique no link abaixo para criar uma nova:\n\n" +
                link + "\n\n" +
                "Se não foi você que solicitou isso, pode ignorar este e-mail com segurança.");

        mailSender.send(mensagem);
    }
}