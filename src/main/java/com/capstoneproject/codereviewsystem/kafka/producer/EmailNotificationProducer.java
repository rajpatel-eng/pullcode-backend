package com.capstoneproject.codereviewsystem.kafka.producer;

import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.EmailNotificationEvent;
import com.capstoneproject.codereviewsystem.kafka.events.EmailNotificationEvent.EmailType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;


    public void publish(EmailNotificationEvent event) {
        kafkaTemplate.send(KafkaTopics.EMAIL_NOTIFICATIONS, event.getToEmail(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish EmailNotificationEvent type={} to={}: {}",
                                event.getEmailType(), event.getToEmail(), ex.getMessage());
                    } else {
                        log.info("EmailNotificationEvent published: type={} to={}",
                                event.getEmailType(), event.getToEmail());
                    }
                });
    }


    public void sendOtp(String toEmail, String otp) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Your OTP - Code Review System")
                .body("Your One-Time Password (OTP) is: " + otp
                        + "\n\nValid for 10 minutes. Do not share this OTP with anyone."
                        + "\n\n— Code Review System")
                .emailType(EmailType.OTP)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendWelcome(String toEmail, String name) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Welcome to Code Review System!")
                .body("Hi " + name + ",\n\nWelcome to Code Review System! "
                        + "Your account has been successfully created.\n\n— Code Review System")
                .emailType(EmailType.WELCOME)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendForgotPassword(String toEmail, String otp) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Reset Your Password - Code Review System")
                .body("You requested a password reset.\n\nYour OTP is: " + otp
                        + "\n\nValid for 10 minutes.\n\n— Code Review System")
                .emailType(EmailType.FORGOT_PASSWORD)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendPasswordChanged(String toEmail, String name) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Password Changed - Code Review System")
                .body("Hi " + name + ",\n\nYour password has been successfully changed."
                        + "\n\nIf you did not make this change, contact support immediately."
                        + "\n\n— Code Review System")
                .emailType(EmailType.PASSWORD_CHANGED)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendAdminOtp(String toEmail, String name, String otp) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Your Admin Login OTP - Code Review System")
                .body("Hi " + name + ",\n\nYour One-Time Password is: " + otp
                        + "\n\nValid for 5 minutes. Do not share this OTP with anyone."
                        + "\n\nIf you did not attempt to log in, contact support immediately."
                        + "\n\n— Code Review System")
                .emailType(EmailType.ADMIN_OTP)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendIamWelcome(String toEmail, String name) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Your IAM Account - Code Review System")
                .body("Hi " + name + ",\n\nAn administrator has created an IAM account for you."
                        + "\n\nYou will need to enter a One-Time Password (OTP) sent to this email each time you log in."
                        + "\n\nWe recommend changing your password after your first login."
                        + "\n\n— Code Review System")
                .emailType(EmailType.IAM_WELCOME)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendModelMigration(String toEmail, String userName, String repoName, String newModelName) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("[Code Review] AI Model Updated for Repository: " + repoName)
                .body("Hi " + userName + ",\n\nThe AI model configured for repository \"" + repoName
                        + "\" has been migrated to the default AI model (" + newModelName
                        + ") because the previously selected model is no longer available."
                        + "\n\nYou can update your repository's AI model at any time from your dashboard."
                        + "\n\n— Code Review System")
                .emailType(EmailType.MODEL_MIGRATION)
                .metadata(Map.of("repoName", repoName, "newModelName", newModelName))
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendHealthAlert(String toEmail, String recipientName,
                                String modelName, String alertType, String alertMessage) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("[ALERT] AI Model Issue Detected: " + modelName + " — " + alertType)
                .body("Hi " + recipientName + ",\n\nA health alert has been triggered for AI model: "
                        + modelName + "\n\nDetails: " + alertMessage
                        + "\n\nPlease log in to the admin panel to review and take action:"
                        + "\n  → Analytics → Model Health → " + modelName
                        + "\n\nThis alert will auto-resolve once the condition is no longer detected."
                        + "\n\n— Code Review System")
                .emailType(EmailType.HEALTH_ALERT)
                .metadata(Map.of("modelName", modelName, "alertType", alertType))
                .createdAt(LocalDateTime.now())
                .build());
    }

    public void sendCostSpikeAlert(String toEmail, String recipientName,
                                   String modelName, double todayCost, double avgCost) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("[COST ALERT] Unexpected cost spike detected: " + modelName)
                .body("Hi " + recipientName + ",\n\nAn unexpected cost spike has been detected for AI model: "
                        + modelName + "\n\nToday's cost:     $" + String.format("%.4f", todayCost)
                        + "\n7-day average:    $" + String.format("%.4f", avgCost)
                        + "\n\nPlease review usage and API key configuration in the admin panel."
                        + "\n\n— Code Review System")
                .emailType(EmailType.COST_SPIKE_ALERT)
                .metadata(Map.of("modelName", modelName,
                        "todayCost", todayCost, "avgCost", avgCost))
                .createdAt(LocalDateTime.now())
                .build());
    }


    public void sendReviewReport(String toEmail, String userName,
                                 String commitId, String pdfBase64, String pdfFileName) {
        publish(EmailNotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .toEmail(toEmail)
                .subject("Code Review Report — " + commitId + " — Code Review System")
                .body("Hi " + userName + ",\n\nYour code review for commit " + commitId
                        + " is complete. Please find the detailed PDF report attached."
                        + "\n\nLog in to the dashboard to explore interactive results."
                        + "\n\n— Code Review System")
                .emailType(EmailType.REVIEW_REPORT)
                .pdfBase64(pdfBase64)
                .pdfFileName(pdfFileName)
                .metadata(Map.of("commitId", commitId))
                .createdAt(LocalDateTime.now())
                .build());
    }
}