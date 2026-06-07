package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.entity.*;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord.ReviewOutcome;
import com.capstoneproject.codereviewsystem.repos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsSnapshotScheduler {

    private final AiModelRepository             modelRepository;
    private final AiModelReviewRecordRepository reviewRecordRepo;
    private final AiModelUsageStatsRepository   usageStatsRepo;
    private final AiModelDailySnapshotRepository snapshotRepo;
    private final AiModelHealthAlertRepository  alertRepo;
    private final AnalyticsAggregationService   aggregationService;
    private final HealthMonitoringService       healthMonitoringService;
    private final ObjectMapper                  objectMapper;


    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void runNightlySnapshot() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Starting nightly analytics snapshot for date: {}", yesterday);

        List<AiModel> models = modelRepository.findByDeletedFalse(
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        for (AiModel model : models) {
            try {
                aggregateDailyStats(model, yesterday);
                writeDailySnapshot(model, yesterday);
            } catch (Exception e) {
                log.error("Snapshot failed for model {} on {}: {}",
                        model.getName(), yesterday, e.getMessage());
            }
        }

        log.info("Nightly snapshot complete for {} models", models.size());
    }


    @Scheduled(fixedDelayString = "${app.analytics.health-check-interval-ms:900000}")
    public void runHealthCheck() {
        log.debug("Running AI model health checks");
        modelRepository.findByActiveTrueAndDeletedFalse().forEach(model -> {
            try {
                healthMonitoringService.evaluateAndAlert(model);
            } catch (Exception e) {
                log.error("Health check failed for model {}: {}", model.getName(), e.getMessage());
            }
        });
    }


    @Transactional
    public void aggregateDailyStats(AiModel model, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.plusDays(1).atStartOfDay();

        List<AiModelReviewRecord> records = reviewRecordRepo
                .findRecentByModel(model, from)
                .stream()
                .filter(r -> r.getReviewedAt().isBefore(to))
                .toList();

        if (records.isEmpty()) return;

        long totalReviews  = records.size();
        long successCount  = records.stream().filter(r -> r.getOutcome() == ReviewOutcome.SUCCESS).count();
        long failureCount  = records.stream().filter(r -> r.getOutcome() == ReviewOutcome.FAILED).count();
        long timeoutCount  = records.stream().filter(r -> r.getOutcome() == ReviewOutcome.TIMEOUT).count();
        long rateLimCount  = records.stream().filter(r -> r.getOutcome() == ReviewOutcome.RATE_LIMITED).count();

        long inputTokens   = records.stream().mapToLong(AiModelReviewRecord::getInputTokens).sum();
        long outputTokens  = records.stream().mapToLong(AiModelReviewRecord::getOutputTokens).sum();
        long totalTokens   = records.stream().mapToLong(AiModelReviewRecord::getTotalTokens).sum();

        BigDecimal totalCost = records.stream()
                .map(AiModelReviewRecord::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long avgLatency = (long) records.stream()
                .filter(r -> r.getLatencyMs() != null)
                .mapToLong(AiModelReviewRecord::getLatencyMs)
                .average().orElse(0.0);

        long p95Latency = aggregationService.computeP95Latency(model, from, to);

        long avgGenMs = (long) records.stream()
                .filter(r -> r.getReviewGenerationMs() != null)
                .mapToLong(AiModelReviewRecord::getReviewGenerationMs)
                .average().orElse(0.0);

        BigDecimal scoreSum = records.stream()
                .filter(r -> r.getReviewScore() != null)
                .map(AiModelReviewRecord::getReviewScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long reviewsWithScore = records.stream().filter(r -> r.getReviewScore() != null).count();

        BigDecimal ratingSum = records.stream()
                .filter(r -> r.getUserRating() != null)
                .map(r -> BigDecimal.valueOf(r.getUserRating()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long reviewsWithRating = records.stream().filter(r -> r.getUserRating() != null).count();

        long helpfulCount   = records.stream().filter(AiModelReviewRecord::isMarkedHelpful).count();
        long acceptedCount  = records.stream().filter(AiModelReviewRecord::isSuggestionAccepted).count();

        AiModelUsageStats stats = usageStatsRepo
                .findByAiModelAndStatDate(model, date)
                .orElse(AiModelUsageStats.builder().aiModel(model).statDate(date).build());

        stats.setTotalReviews((int) totalReviews);
        stats.setSuccessCount((int) successCount);
        stats.setFailureCount((int) failureCount);
        stats.setTimeoutCount((int) timeoutCount);
        stats.setRateLimitCount((int) rateLimCount);
        stats.setInputTokens(inputTokens);
        stats.setOutputTokens(outputTokens);
        stats.setTotalTokens(totalTokens);
        stats.setTotalCost(totalCost);
        stats.setAvgLatencyMs(avgLatency);
        stats.setP95LatencyMs(p95Latency);
        stats.setAvgReviewGenerationMs(avgGenMs);
        stats.setReviewScoreSum(scoreSum);
        stats.setReviewsWithScore((int) reviewsWithScore);
        stats.setUserRatingSum(ratingSum);
        stats.setReviewsWithRating((int) reviewsWithRating);
        stats.setHelpfulCount((int) helpfulCount);
        stats.setAcceptedSuggestionsCount((int) acceptedCount);

        usageStatsRepo.save(stats);
        log.debug("Aggregated daily stats for model {} on {}: {} reviews", model.getName(), date, totalReviews);
    }


    @Transactional
    public void writeDailySnapshot(AiModel model, LocalDate date) {
        var usageM   = aggregationService.computeUsageMetrics(model);
        var perfM    = aggregationService.computePerformanceMetrics(model);
        var costM    = aggregationService.computeCostMetrics(model);
        var qualityM = aggregationService.computeQualityMetrics(model);
        var health   = aggregationService.computeHealthStatus(model);

        AiModelDailySnapshot snapshot = snapshotRepo
                .findByAiModelAndSnapshotDate(model, date)
                .orElse(AiModelDailySnapshot.builder().aiModel(model).snapshotDate(date).build());

        snapshot.setUsageMetricsJson(toJson(Map.of(
                "totalReviews",       usageM.getTotalReviews(),
                "repositoriesCount",  usageM.getTotalRepositoriesUsing(),
                "totalTokens",        usageM.getTotalTokens(),
                "inputTokens",        usageM.getInputTokens(),
                "outputTokens",       usageM.getOutputTokens()
        )));

        snapshot.setPerformanceMetricsJson(toJson(Map.of(
                "avgLatencyMs",        perfM.getAvgResponseTimeMs(),
                "p95LatencyMs",        perfM.getP95ResponseTimeMs(),
                "successRate",         perfM.getSuccessRate(),
                "failureRate",         perfM.getFailureRate(),
                "timeoutCount",        perfM.getTimeoutCount(),
                "rateLimitCount",      perfM.getRateLimitCount(),
                "avgReviewGenMs",      perfM.getAvgReviewGenerationMs()
        )));

        snapshot.setCostMetricsJson(toJson(Map.of(
                "totalCost",               costM.getTotalCost(),
                "costThisMonth",           costM.getCostThisMonth(),
                "avgCostPerReview",        costM.getAvgCostPerReview(),
                "avgCostPerRepo",          costM.getAvgCostPerRepository(),
                "monthlyProjection",       costM.getEstimatedMonthlyProjection()
        )));

        snapshot.setQualityMetricsJson(toJson(Map.of(
                "avgReviewScore",           qualityM.getAvgReviewScore(),
                "userRatingAvg",            qualityM.getUserFeedbackRating(),
                "helpfulPct",               qualityM.getHelpfulReviewPercentage(),
                "acceptanceRate",           qualityM.getAcceptanceRate(),
                "falsePositiveRate",        qualityM.getFalsePositiveRate(),
                "falseNegativeRate",        qualityM.getFalseNegativeRate()
        )));

        snapshot.setHealthStatus(health);
        snapshotRepo.save(snapshot);
        log.debug("Wrote daily snapshot for model {} on {} — health: {}", model.getName(), date, health);
    }


    private String toJson(Map<String, ?> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("JSON serialization failed: {}", e.getMessage());
            return "{}";
        }
    }
}
