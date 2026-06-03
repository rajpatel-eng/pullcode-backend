package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelComparisonService {

    private final AiModelRepository           modelRepository;
    private final AnalyticsAggregationService aggregationService;
    private final TrendAnalyticsService       trendService;
    private final RecommendationService       recommendationService;

    /**
     * Builds a full comparison response for the requested model IDs.
     * Validates that 2–5 models are requested (1 is pointless, 6+ is too noisy).
     */
    public ComparisonResponse compare(List<Long> modelIds, TrendPeriod period) {
        if (modelIds == null || modelIds.size() < 2) {
            throw new BadRequestException("At least 2 models are required for comparison");
        }
        if (modelIds.size() > 5) {
            throw new BadRequestException("Maximum 5 models can be compared at once");
        }

        long totalSystemRepos = modelRepository.findByActiveTrueAndDeletedFalse().size();

        List<ModelComparisonRow> rows = modelIds.stream()
                .map(id -> buildComparisonRow(id, totalSystemRepos))
                .toList();

        List<String> recommendations = buildComparisonRecommendations(rows);

        return ComparisonResponse.builder()
                .modelIds(modelIds)
                .rows(rows)
                .usageTrends(trendService.usageTrendsForModels(modelIds, period))
                .costTrends(trendService.costTrendsForModels(modelIds, period))
                .performanceTrends(trendService.performanceTrendsForModels(modelIds, period))
                .recommendations(recommendations)
                .build();
    }

    // ── Builds one row in the comparison table ────────────────────────────────

    private ModelComparisonRow buildComparisonRow(Long modelId, long totalRepos) {
        AiModel model = modelRepository.findByIdAndDeletedFalse(modelId)
                .orElseThrow(() -> new BadRequestException("Model not found: " + modelId));

        UsageMetrics       usage   = aggregationService.computeUsageMetrics(model);
        PerformanceMetrics perf    = aggregationService.computePerformanceMetrics(model);
        QualityMetrics     quality = aggregationService.computeQualityMetrics(model);
        CostMetrics        cost    = aggregationService.computeCostMetrics(model);
        AdoptionMetrics    adopt   = aggregationService.computeAdoptionMetrics(model, totalRepos);

        ModelHealthStatus health = aggregationService.computeHealthStatus(model);

        return ModelComparisonRow.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .provider(model.getProvider())
                .isDefault(model.isDefaultModel())
                .healthStatus(health)
                // Usage
                .totalReviews(usage.getTotalReviews())
                .repositoriesUsing(usage.getTotalRepositoriesUsing())
                // Cost
                .totalCost(cost.getTotalCost())
                .avgCostPerReview(cost.getAvgCostPerReview())
                // Performance
                .avgResponseTimeMs(perf.getAvgResponseTimeMs())
                .p95ResponseTimeMs(perf.getP95ResponseTimeMs())
                .successRate(perf.getSuccessRate())
                // Quality
                .avgReviewScore(quality.getAvgReviewScore())
                .userFeedbackRating(quality.getUserFeedbackRating())
                // Adoption
                .marketSharePercentage(adopt.getMarketSharePercentage())
                .repositoryGrowthTrend(adopt.getRepositoryGrowthTrend())
                .build();
    }

    // ── Cross-model textual recommendations ──────────────────────────────────

    private List<String> buildComparisonRecommendations(List<ModelComparisonRow> rows) {
        if (rows.isEmpty()) return List.of();

        // Find best and worst performers on key dimensions
        ModelComparisonRow bestSuccess = rows.stream()
                .max(java.util.Comparator.comparingDouble(ModelComparisonRow::getSuccessRate))
                .orElse(rows.get(0));

        ModelComparisonRow lowestCost = rows.stream()
                .filter(r -> r.getAvgCostPerReview().compareTo(java.math.BigDecimal.ZERO) > 0)
                .min(java.util.Comparator.comparing(ModelComparisonRow::getAvgCostPerReview))
                .orElse(rows.get(0));

        ModelComparisonRow highestRating = rows.stream()
                .max(java.util.Comparator.comparingDouble(ModelComparisonRow::getUserFeedbackRating))
                .orElse(rows.get(0));

        ModelComparisonRow fastestModel = rows.stream()
                .min(java.util.Comparator.comparingDouble(ModelComparisonRow::getAvgResponseTimeMs))
                .orElse(rows.get(0));

        ModelComparisonRow mostAdopted = rows.stream()
                .max(java.util.Comparator.comparingDouble(ModelComparisonRow::getMarketSharePercentage))
                .orElse(rows.get(0));

        return List.of(
                String.format("'%s' has the highest success rate (%.1f%%).",
                        bestSuccess.getModelName(), bestSuccess.getSuccessRate()),
                String.format("'%s' is the most cost-efficient at $%.4f per review.",
                        lowestCost.getModelName(), lowestCost.getAvgCostPerReview().doubleValue()),
                String.format("'%s' receives the best user feedback rating (%.1f/5.0).",
                        highestRating.getModelName(), highestRating.getUserFeedbackRating()),
                String.format("'%s' is the fastest with %.0fms average response time.",
                        fastestModel.getModelName(), fastestModel.getAvgResponseTimeMs()),
                String.format("'%s' has the highest adoption with %.1f%% market share.",
                        mostAdopted.getModelName(), mostAdopted.getMarketSharePercentage())
        );
    }
}
