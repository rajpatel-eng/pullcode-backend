package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiModelRepository           modelRepository;
    private final AnalyticsAggregationService aggregationService;

    // Thresholds
    private static final double LOW_SUCCESS_RATE      = 80.0;
    private static final double HIGH_ERROR_RATE       = 15.0;
    private static final double HIGH_LATENCY_MS       = 10_000.0;
    private static final double LOW_USER_RATING       = 2.5;
    private static final double LOW_ADOPTION_SHARE    = 5.0;
    private static final double HIGH_COST_MULTIPLIER  = 2.0;  // 2x avg cost = high cost

    /**
     * Generates recommendations for a single model by comparing it against
     * all other active models in the system.
     */
    public List<Recommendation> recommendationsForModel(AiModel model) {
        List<Recommendation> recs = new ArrayList<>();

        PerformanceMetrics perf    = aggregationService.computePerformanceMetrics(model);
        QualityMetrics     quality = aggregationService.computeQualityMetrics(model);
        CostMetrics        cost    = aggregationService.computeCostMetrics(model);
        AdoptionMetrics    adopt   = aggregationService.computeAdoptionMetrics(model,
                modelRepository.findByActiveTrueAndDeletedFalse().size());

        // ── Success rate low ──────────────────────────────────────────────────
        if (perf.getSuccessRate() < LOW_SUCCESS_RATE) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("INVESTIGATE")
                    .reason(String.format(
                            "Success rate is %.1f%% (threshold: %.0f%%). " +
                            "Review recent error logs and check provider status.",
                            perf.getSuccessRate(), LOW_SUCCESS_RATE))
                    .impact("Prevents further degradation in review quality for users")
                    .priority(1)
                    .build());
        }

        // ── High timeout count ────────────────────────────────────────────────
        if (perf.getTimeoutCount() > 50) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("INVESTIGATE")
                    .reason(String.format(
                            "Model has %d timeouts in the last 30 days. " +
                            "Consider increasing timeout threshold or contacting provider.",
                            perf.getTimeoutCount()))
                    .impact("Reduces failed reviews and improves user experience")
                    .priority(2)
                    .build());
        }

        // ── High latency ──────────────────────────────────────────────────────
        if (perf.getAvgResponseTimeMs() > HIGH_LATENCY_MS) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("INVESTIGATE")
                    .reason(String.format(
                            "Average response time is %.0fms — above the %.0fms threshold. " +
                            "Users may be experiencing slow reviews.",
                            perf.getAvgResponseTimeMs(), HIGH_LATENCY_MS))
                    .impact("Improving latency directly increases user satisfaction")
                    .priority(2)
                    .build());
        }

        // ── Low user rating ───────────────────────────────────────────────────
        if (quality.getUserFeedbackRating() > 0 &&
                quality.getUserFeedbackRating() < LOW_USER_RATING) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("PAUSE")
                    .reason(String.format(
                            "User feedback rating is %.1f/5.0 — below acceptable threshold of %.1f. " +
                            "Consider pausing and reviewing prompts or switching to a better model.",
                            quality.getUserFeedbackRating(), LOW_USER_RATING))
                    .impact("Stops users from receiving low-quality reviews")
                    .priority(1)
                    .build());
        }

        // ── Low adoption ─────────────────────────────────────────────────────
        if (adopt.getMarketSharePercentage() < LOW_ADOPTION_SHARE &&
                adopt.getCurrentRepositories() > 0) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("PROMOTE")
                    .reason(String.format(
                            "Market share is %.1f%% — only %d repositories are using this model. " +
                            "Consider promoting it or making it the default if quality is high.",
                            adopt.getMarketSharePercentage(), adopt.getCurrentRepositories()))
                    .impact("Increases model utilization and potentially lowers cost per review")
                    .priority(3)
                    .build());
        }

        // ── Cost vs average comparison ────────────────────────────────────────
        if (cost.getAvgCostPerReview().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal systemAvgCost = computeSystemAvgCostPerReview();
            if (systemAvgCost.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = cost.getAvgCostPerReview()
                        .divide(systemAvgCost, 4, java.math.RoundingMode.HALF_UP)
                        .doubleValue();
                if (ratio > HIGH_COST_MULTIPLIER) {
                    recs.add(Recommendation.builder()
                            .aiModelId(model.getId())
                            .modelName(model.getName())
                            .type("INVESTIGATE")
                            .reason(String.format(
                                    "Cost per review ($%.4f) is %.1fx higher than the system average ($%.4f). " +
                                    "Evaluate if the quality justifies the cost.",
                                    cost.getAvgCostPerReview().doubleValue(),
                                    ratio,
                                    systemAvgCost.doubleValue()))
                            .impact("Reducing cost could save significant budget at scale")
                            .priority(3)
                            .build());
                }
            }
        }

        // ── Positive: CHANGE_DEFAULT candidate ───────────────────────────────
        if (perf.getSuccessRate() >= 99.0
                && quality.getUserFeedbackRating() >= 4.5
                && !model.isDefaultModel()) {
            recs.add(Recommendation.builder()
                    .aiModelId(model.getId())
                    .modelName(model.getName())
                    .type("CHANGE_DEFAULT")
                    .reason(String.format(
                            "Model has %.1f%% success rate and %.1f/5.0 user rating — " +
                            "the best performing model in the system. Consider setting it as default.",
                            perf.getSuccessRate(), quality.getUserFeedbackRating()))
                    .impact("All new repositories will benefit from top-quality reviews")
                    .priority(4)
                    .build());
        }

        // Sort by priority ascending (1 = most urgent)
        recs.sort(Comparator.comparingInt(Recommendation::getPriority));
        return recs;
    }

    /**
     * Generates recommendations across all active models — used for the
     * system-wide analytics summary dashboard.
     */
    public List<Recommendation> allRecommendations() {
        List<Recommendation> all = new ArrayList<>();
        modelRepository.findByActiveTrueAndDeletedFalse()
                .forEach(m -> all.addAll(recommendationsForModel(m)));
        all.sort(Comparator.comparingInt(Recommendation::getPriority));
        return all.stream().limit(10).toList(); // top 10 for dashboard
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal computeSystemAvgCostPerReview() {
        List<AiModel> activeModels = modelRepository.findByActiveTrueAndDeletedFalse();
        if (activeModels.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalCost    = BigDecimal.ZERO;
        long       totalReviews = 0L;

        for (AiModel m : activeModels) {
            CostMetrics c = aggregationService.computeCostMetrics(m);
            totalCost    = totalCost.add(c.getTotalCost());
            UsageMetrics u = aggregationService.computeUsageMetrics(m);
            totalReviews += u.getTotalReviews();
        }

        return totalReviews > 0
                ? totalCost.divide(BigDecimal.valueOf(totalReviews), 6, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }
}
