package com.capstoneproject.codereviewsystem.kafka.consumer;

import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.EmailNotificationEvent;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = KafkaTopics.EMAIL_NOTIFICATIONS,
            groupId = "email-notification-group",
            containerFactory = "emailKafkaListenerContainerFactory"
    )
    public void consume(EmailNotificationEvent event) {
        log.info("EmailNotificationConsumer: type={} to={} eventId={}",
                event.getEmailType(), event.getToEmail(), event.getEventId());
        try {
            if (event.getPdfBase64() != null && !event.getPdfBase64().isBlank()) {
                emailService.sendEmailWithPdfAttachment(
                        event.getToEmail(),
                        event.getSubject(),
                        event.getBody(),
                        event.getPdfBase64(),
                        event.getPdfFileName() != null ? event.getPdfFileName() : "review-report.pdf"
                );
            } else {
                emailService.sendEmail(
                        event.getToEmail(),
                        event.getSubject(),
                        event.getBody()
                );
            }
        } catch (Exception e) {
            log.error("EmailNotificationConsumer: failed to send type={} to={}: {}",
                    event.getEmailType(), event.getToEmail(), e.getMessage());
            throw new RuntimeException("Email delivery failed", e);
        }
    }
}