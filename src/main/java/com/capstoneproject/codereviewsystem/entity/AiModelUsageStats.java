package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ai_model_usage_stats",
        uniqueConstraints = {
            @UniqueConstraint(
                name = "uq_usage_model_date",
                columnNames = {"ai_model_id", "stat_date"})
        },
        indexes = {
            @Index(name = "idx_usage_model_id",   columnList = "ai_model_id"),
            @Index(name = "idx_usage_stat_date",  columnList = "stat_date"),
            @Index(name = "idx_usage_model_date", columnList = "ai_model_id, stat_date")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModelUsageStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id", nullable = false)
    private AiModel aiModel;

    @Column(nullable = false)
    private LocalDate statDate;

    @Builder.Default
    private Integer repositoriesCount = 0;

    @Builder.Default
    private Integer totalReviews = 0;

    @Builder.Default
    private Integer successCount = 0;

    @Builder.Default
    private Integer failureCount = 0;

    @Builder.Default
    private Integer timeoutCount = 0;

    @Builder.Default
    private Integer rateLimitCount = 0;

    @Builder.Default
    private Long inputTokens = 0L;

    @Builder.Default
    private Long outputTokens = 0L;

    @Builder.Default
    private Long totalTokens = 0L;

    @Builder.Default
    private Long avgLatencyMs = 0L;

    @Builder.Default
    private Long p95LatencyMs = 0L;

    @Builder.Default
    private Long avgReviewGenerationMs = 0L;

    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal reviewScoreSum = BigDecimal.ZERO;

    @Builder.Default
    private Integer reviewsWithScore = 0;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal userRatingSum = BigDecimal.ZERO;

    @Builder.Default
    private Integer reviewsWithRating = 0;

    @Builder.Default
    private Integer acceptedSuggestionsCount = 0;

    @Builder.Default
    private Integer helpfulCount = 0;
}