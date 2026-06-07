package com.capstoneproject.codereviewsystem.services.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendEmail(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to: {} | subject: {}", toEmail, subject);
        } catch (Exception e) {
            log.error("Email failed to: {} | error: {}", toEmail, e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }


    public void sendEmailWithPdfAttachment(String toEmail, String subject,
                                           String body, String pdfBase64, String fileName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body);

            byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes), "application/pdf");

            mailSender.send(message);
            log.info("Email+PDF sent to: {} | subject: {} | attachment: {}", toEmail, subject, fileName);
        } catch (MessagingException e) {
            log.error("Email+PDF failed to: {} | error: {}", toEmail, e.getMessage());
            throw new RuntimeException("Email with PDF attachment failed", e);
        }
    }
}