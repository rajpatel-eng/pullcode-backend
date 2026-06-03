package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "ai_model_daily_snapshots",
        uniqueConstraints = {
            @UniqueConstraint(
                name = "uq_snapshot_model_date",
                columnNames = {"ai_model_id", "snapshot_date"})
        },
        indexes = {
            @Index(name = "idx_snapshot_model_id",   columnList = "ai_model_id"),
            @Index(name = "idx_snapshot_date",       columnList = "snapshot_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id", nullable = false)
    private AiModel aiModel;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(columnDefinition = "TEXT")
    private String usageMetricsJson;

    @Column(columnDefinition = "TEXT")
    private String performanceMetricsJson;

    @Column(columnDefinition = "TEXT")
    private String costMetricsJson;

    @Column(columnDefinition = "TEXT")
    private String qualityMetricsJson;
    
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModelHealthStatus healthStatus;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum ModelHealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        OFFLINE
    }
}