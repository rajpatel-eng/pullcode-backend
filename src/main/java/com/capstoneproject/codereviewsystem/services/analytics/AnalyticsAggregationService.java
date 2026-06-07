package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.*;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord.ReviewOutcome;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsAggregationService {

    private final AiModelRepository            modelRepository;
    private final AiModelUsageStatsRepository  usageStatsRepo;
    private final AiModelReviewRecordRepository reviewRecordRepo;
    private final CodeRepositoryRepository     repoRepository;
    private final AiModelDailySnapshotRepository snapshotRepo;

    public UsageMetrics computeUsageMetrics(AiModel model) {
        LocalDate today     = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        LocalDate monthStart = today.withDayOfMonth(1);

        long totalRepos  = repoRepository.findByAiModel(model).size();
        long activeRepos = repoRepository.findByAiModel(model).stream()
                .filter(r -> r.getAiModel() != null)
                .count();

        long totalReviews = usageStatsRepo.sumTotalReviews(model, LocalDate.of(2020, 1, 1), today);
        long reviewsToday = usageStatsRepo.sumReviewsOnDate(model, today);
        long reviewsWeek  = usageStatsRepo.sumTotalReviews(model, weekStart, today);
        long reviewsMonth = usageStatsRepo.sumTotalReviews(model, monthStart, today);

        long totalTokens  = usageStatsRepo.sumTotalTokens(model, LocalDate.of(2020, 1, 1), today);

        long inputTokens = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(model, LocalDate.of(2020, 1, 1), today)
                .stream().mapToLong(AiModelUsageStats::getInputTokens).sum();
        long outputTokens = totalTokens - inputTokens;

        return UsageMetrics.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .provider(model.getProvider())
                .totalRepositoriesUsing(totalRepos)
                .activeRepositoriesUsing(activeRepos)
                .totalReviews(totalReviews)
                .reviewsToday(reviewsToday)
                .reviewsThisWeek(reviewsWeek)
                .reviewsThisMonth(reviewsMonth)
                .totalTokens(totalTokens)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .build();
    }

    public PerformanceMetrics computePerformanceMetrics(AiModel model) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to   = LocalDateTime.now();

        long total    = reviewRecordRepo.countByModelAndDateRange(model, from, to);
        long success  = reviewRecordRepo.countByModelOutcomeAndDateRange(model, ReviewOutcome.SUCCESS, from, to);
        long failed   = reviewRecordRepo.countByModelOutcomeAndDateRange(model, ReviewOutcome.FAILED, from, to);
        long timeouts = reviewRecordRepo.countByModelOutcomeAndDateRange(model, ReviewOutcome.TIMEOUT, from, to);
        long rateLim  = reviewRecordRepo.countByModelOutcomeAndDateRange(model, ReviewOutcome.RATE_LIMITED, from, to);

        double successRate = total > 0 ? (success * 100.0 / total) : 0.0;
        double failureRate = total > 0 ? (failed  * 100.0 / total) : 0.0;

        Double avgLatency = reviewRecordRepo.avgLatencyByModelAndDateRange(model, from, to);
        long   p95        = computeP95Latency(model, from, to);

        double avgGenMs = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, from.toLocalDate(), to.toLocalDate())
                .stream()
                .mapToLong(AiModelUsageStats::getAvgReviewGenerationMs)
                .average().orElse(0.0);

        ModelHealthStatus health = snapshotRepo.findTopByAiModelOrderBySnapshotDateDesc(model)
                .map(AiModelDailySnapshot::getHealthStatus)
                .orElse(ModelHealthStatus.HEALTHY);

        return PerformanceMetrics.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .avgResponseTimeMs(avgLatency != null ? round2(avgLatency) : 0.0)
                .p95ResponseTimeMs(p95)
                .successRate(round2(successRate))
                .failureRate(round2(failureRate))
                .timeoutCount(timeouts)
                .rateLimitCount(rateLim)
                .avgReviewGenerationMs(round2(avgGenMs))
                .healthStatus(health)
                .build();
    }


    public QualityMetrics computeQualityMetrics(AiModel model) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to   = LocalDateTime.now();

        long total = reviewRecordRepo.countByModelAndDateRange(model, from, to);

        Double avgScore  = reviewRecordRepo.avgReviewScoreByModel(model, from, to);
        Double avgRating = reviewRecordRepo.avgUserRatingByModel(model, from, to);
        long   fps       = reviewRecordRepo.countFalsePositives(model, from, to);
        long   fns       = reviewRecordRepo.countFalseNegatives(model, from, to);

        long helpfulSum  = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, from.toLocalDate(), to.toLocalDate())
                .stream().mapToLong(AiModelUsageStats::getHelpfulCount).sum();
        long acceptedSum = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, from.toLocalDate(), to.toLocalDate())
                .stream().mapToLong(AiModelUsageStats::getAcceptedSuggestionsCount).sum();

        double helpfulPct    = total > 0 ? round2(helpfulSum  * 100.0 / total) : 0.0;
        double acceptancePct = total > 0 ? round2(acceptedSum * 100.0 / total) : 0.0;
        double fpRate        = total > 0 ? round2(fps * 100.0 / total) : 0.0;
        double fnRate        = total > 0 ? round2(fns * 100.0 / total) : 0.0;

        return QualityMetrics.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .avgReviewScore(avgScore  != null ? round2(avgScore)  : 0.0)
                .userFeedbackRating(avgRating != null ? round2(avgRating) : 0.0)
                .helpfulReviewPercentage(helpfulPct)
                .acceptanceRate(acceptancePct)
                .falsePositiveRate(fpRate)
                .falseNegativeRate(fnRate)
                .build();
    }

    public CostMetrics computeCostMetrics(AiModel model) {
        LocalDate today      = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd   = today.with(TemporalAdjusters.lastDayOfMonth());

        BigDecimal totalCost  = usageStatsRepo.sumTotalCost(model, LocalDate.of(2020, 1, 1), today);
        BigDecimal todayCost  = Optional(usageStatsRepo.findByAiModelAndStatDate(model, today))
                                .map(AiModelUsageStats::getTotalCost)
                                .orElse(BigDecimal.ZERO);
        BigDecimal monthCost  = usageStatsRepo.sumTotalCost(model, monthStart, today);

        long totalReviews = usageStatsRepo.sumTotalReviews(model, LocalDate.of(2020, 1, 1), today);

        long totalRepos = repoRepository.findByAiModel(model).size();

        BigDecimal avgPerReview = totalReviews > 0
                ? totalCost.divide(BigDecimal.valueOf(totalReviews), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal avgPerRepo = totalRepos > 0
                ? totalCost.divide(BigDecimal.valueOf(totalRepos), 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int daysInMonth  = monthEnd.getDayOfMonth();
        int daysPassed   = today.getDayOfMonth();
        BigDecimal projection = daysPassed > 0
                ? monthCost.multiply(BigDecimal.valueOf((double) daysInMonth / daysPassed))
                        .setScale(6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return CostMetrics.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .provider(model.getProvider())
                .totalCost(totalCost)
                .costToday(todayCost)
                .costThisMonth(monthCost)
                .avgCostPerReview(avgPerReview)
                .avgCostPerRepository(avgPerRepo)
                .estimatedMonthlyProjection(projection)
                .build();
    }

    public AdoptionMetrics computeAdoptionMetrics(AiModel model,
                                                   long totalSystemRepos) {
        LocalDate thisMonthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate prevMonthStart = thisMonthStart.minusMonths(1);

        long current = repoRepository.findByAiModel(model).size();

        long newThisMonth = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, thisMonthStart, LocalDate.now())
                .stream().mapToLong(s -> s.getRepositoriesCount()).max().orElse(0L)
                - usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, prevMonthStart, thisMonthStart.minusDays(1))
                .stream().mapToLong(s -> s.getRepositoriesCount()).max().orElse(0L);

        long prevMonth = usageStatsRepo
                .findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                        model, prevMonthStart, thisMonthStart.minusDays(1))
                .stream().mapToLong(AiModelUsageStats::getRepositoriesCount).max().orElse(0L);

        double growthTrend = prevMonth > 0
                ? round2(((double)(current - prevMonth) / prevMonth) * 100.0)
                : 0.0;

        double marketShare = totalSystemRepos > 0
                ? round2((current * 100.0) / totalSystemRepos)
                : 0.0;

        return AdoptionMetrics.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .currentRepositories(current)
                .newRepositoriesThisMonth(Math.max(newThisMonth, 0))
                .repositoriesMigratedAway(0L)   // populated by migration event listener
                .repositoryGrowthTrend(growthTrend)
                .marketSharePercentage(marketShare)
                .build();
    }

    public long computeP95Latency(AiModel model, LocalDateTime from, LocalDateTime to) {
        List<Long> latencies = reviewRecordRepo.findLatenciesBetween(model, from, to);
        if (latencies.isEmpty()) return 0L;
        int index = (int) Math.ceil(latencies.size() * 0.95) - 1;
        return latencies.get(Math.min(index, latencies.size() - 1));
    }

    public ModelHealthStatus computeHealthStatus(AiModel model) {
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<AiModelReviewRecord> recent = reviewRecordRepo.findRecentByModel(model, since);

        if (recent.isEmpty()) return ModelHealthStatus.HEALTHY;

        long total    = recent.size();
        long success  = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.SUCCESS).count();
        long timeouts = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.TIMEOUT).count();

        double successRate = (success * 100.0) / total;
        double timeoutRate = (timeouts * 100.0) / total;

        if (successRate >= 95.0 && timeoutRate < 2.0)  return ModelHealthStatus.HEALTHY;
        if (successRate >= 80.0 && timeoutRate < 10.0) return ModelHealthStatus.DEGRADED;
        if (successRate >= 50.0)                        return ModelHealthStatus.UNHEALTHY;
        return ModelHealthStatus.OFFLINE;
    }


    public AiModel requireModel(Long modelId) {
        return modelRepository.findByIdAndDeletedFalse(modelId)
                .orElseThrow(() -> new BadRequestException("AI model not found: " + modelId));
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private <T> java.util.Optional<T> Optional(java.util.Optional<T> opt) {
        return opt;
    }
}
