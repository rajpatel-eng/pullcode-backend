package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_model_health_alerts",
        indexes = {
            @Index(name = "idx_alert_model_id",  columnList = "ai_model_id"),
            @Index(name = "idx_alert_resolved",  columnList = "resolved"),
            @Index(name = "idx_alert_created",   columnList = "created_at")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelHealthAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id", nullable = false)
    private AiModel aiModel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AlertType alertType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    private String triggerValue;

    private String thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.WARNING;

    @Builder.Default
    private boolean resolved = false;

    private LocalDateTime resolvedAt;

    @Builder.Default
    private boolean notified = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AlertType {
        SUCCESS_RATE_LOW,
        ERROR_RATE_HIGH,
        LATENCY_HIGH,
        COST_SPIKE,
        RATE_LIMIT_HIGH,
        TIMEOUT_HIGH,
        API_UNAVAILABLE
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}