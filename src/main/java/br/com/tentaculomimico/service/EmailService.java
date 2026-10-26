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

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void enviarEmailVerificacao(String destinatario, String token) {
        SimpleMailMessage mensagem = new SimpleMailMessage();

        mensagem.setTo(destinatario);
        mensagem.setSubject("Confirme seu e-mail - Tentáculo Mímico");

        String link = baseUrl + "/confirmar-email?token=" + token;

        mensagem.setText("Olá!\n\n" +
                "Obrigado por se cadastrar. Para ativar sua conta, confirme seu e-mail pelo link abaixo:\n\n" +
                link + "\n\n" +
                "O link vale por 24 horas. Se você não criou esta conta, pode ignorar este e-mail.");

        mailSender.send(mensagem);
    }

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