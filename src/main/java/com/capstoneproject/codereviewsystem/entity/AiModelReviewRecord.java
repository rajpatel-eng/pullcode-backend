package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_model_review_records",
        indexes = {
            @Index(name = "idx_review_record_model",      columnList = "ai_model_id"),
            @Index(name = "idx_review_record_date",       columnList = "reviewed_at"),
            @Index(name = "idx_review_record_model_date", columnList = "ai_model_id, reviewed_at"),
            @Index(name = "idx_review_record_commit",     columnList = "commit_history_id"),
            @Index(name = "idx_review_record_repo",       columnList = "repository_id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id", nullable = false)
    private AiModel aiModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private CodeRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_history_id")
    private CommitHistory commitHistory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewOutcome outcome;

    private Long latencyMs;
    private Long reviewGenerationMs;

    @Builder.Default
    private Long inputTokens = 0L;

    @Builder.Default
    private Long outputTokens = 0L;

    @Builder.Default
    private Long totalTokens = 0L;

    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(precision = 4, scale = 2)
    private BigDecimal reviewScore;

    private Integer userRating;

    @Builder.Default
    private boolean markedHelpful = false;

    @Builder.Default
    private boolean suggestionAccepted = false;

    @Builder.Default
    private boolean falsePositive = false;

    @Builder.Default
    private boolean falseNegative = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (reviewedAt == null) reviewedAt = LocalDateTime.now();
    }

    public enum ReviewOutcome {
        SUCCESS,
        FAILED,
        TIMEOUT,
        RATE_LIMITED
    }
}