package com.capstoneproject.codereviewsystem.kafka.events;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailNotificationEvent {

    public enum EmailType {
        OTP,
        WELCOME,
        FORGOT_PASSWORD,
        PASSWORD_CHANGED,
        ADMIN_OTP,
        IAM_WELCOME,
        MODEL_MIGRATION,
        HEALTH_ALERT,
        COST_SPIKE_ALERT,
        REVIEW_REPORT
    }

    private String eventId;

    private String toEmail;

    private String subject;
    
    private String body;

    private EmailType emailType;

    private String pdfBase64;

    private String pdfFileName;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}