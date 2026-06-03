package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.AiModelHealthAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsDashboardService {

    private final AiModelRepository            modelRepository;
    private final AiModelHealthAlertRepository alertRepository;
    private final AnalyticsAggregationService  aggregationService;
    private final HealthMonitoringService      healthService;
    private final RecommendationService        recommendationService;

    // ── Full dashboard for a single model ─────────────────────────────────────

    public ModelDashboard getModelDashboard(Long modelId) {
        AiModel model = modelRepository.findByIdAndDeletedFalse(modelId)
                .orElseThrow(() -> new BadRequestException("AI model not found: " + modelId));

        long totalSystemRepos = modelRepository.findByActiveTrueAndDeletedFalse().size();

        UsageMetrics       usage   = aggregationService.computeUsageMetrics(model);
        PerformanceMetrics perf    = aggregationService.computePerformanceMetrics(model);
        QualityMetrics     quality = aggregationService.computeQualityMetrics(model);
        CostMetrics        cost    = aggregationService.computeCostMetrics(model);
        AdoptionMetrics    adopt   = aggregationService.computeAdoptionMetrics(model, totalSystemRepos);
        ModelHealthStatus  health  = aggregationService.computeHealthStatus(model);

        List<String> recs = recommendationService.recommendationsForModel(model)
                .stream().map(Recommendation::getReason).toList();

        List<AlertSummary> alerts = healthService.getAlertsForModel(modelId);

        return ModelDashboard.builder()
                .aiModelId(model.getId())
                .modelName(model.getName())
                .provider(model.getProvider())
                .active(model.isActive())
                .defaultModel(model.isDefaultModel())
                .healthStatus(health)
                .usage(usage)
                .performance(perf)
                .quality(quality)
                .cost(cost)
                .adoption(adopt)
                .recommendations(recs)
                .activeAlerts(alerts)
                .build();
    }

    // ── System-wide analytics summary ─────────────────────────────────────────

    public SystemAnalyticsSummary getSystemSummary() {
        List<AiModel> allActive = modelRepository.findByActiveTrueAndDeletedFalse();
        int totalModels   = (int) modelRepository.findByDeletedFalse(
                PageRequest.of(0, Integer.MAX_VALUE)).getTotalElements();
        int activeModels  = allActive.size();

        // Health counts
        long healthy   = 0, degraded = 0, unhealthy = 0;
        for (AiModel m : allActive) {
            ModelHealthStatus h = aggregationService.computeHealthStatus(m);
            if (h == ModelHealthStatus.HEALTHY)   healthy++;
            else if (h == ModelHealthStatus.DEGRADED) degraded++;
            else unhealthy++;
        }

        // Reviews today and this month across all models
        long totalReviewsToday  = 0, totalReviewsMonth = 0;
        BigDecimal totalCostMonth = BigDecimal.ZERO;
        for (AiModel m : allActive) {
            UsageMetrics u = aggregationService.computeUsageMetrics(m);
            CostMetrics  c = aggregationService.computeCostMetrics(m);
            totalReviewsToday  += u.getReviewsToday();
            totalReviewsMonth  += u.getReviewsThisMonth();
            totalCostMonth      = totalCostMonth.add(c.getCostThisMonth());
        }

        // Best performers
        String bestPerforming = allActive.stream()
                .max(java.util.Comparator.comparingDouble(m ->
                        aggregationService.computePerformanceMetrics(m).getSuccessRate()))
                .map(AiModel::getName).orElse("N/A");

        String mostUsed = allActive.stream()
                .max(java.util.Comparator.comparingLong(m ->
                        aggregationService.computeUsageMetrics(m).getTotalReviews()))
                .map(AiModel::getName).orElse("N/A");

        String mostEfficient = allActive.stream()
                .filter(m -> aggregationService.computeCostMetrics(m)
                        .getAvgCostPerReview().compareTo(BigDecimal.ZERO) > 0)
                .min(java.util.Comparator.comparing(m ->
                        aggregationService.computeCostMetrics(m).getAvgCostPerReview()))
                .map(AiModel::getName).orElse("N/A");

        long unresolvedAlerts = alertRepository.countUnresolvedSince(
                java.time.LocalDateTime.now().minusDays(7));

        List<AlertSummary> criticalAlerts = healthService.getAllUnresolvedAlerts()
                .stream()
                .filter(a -> a.getSeverity() ==
                        com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertSeverity.CRITICAL)
                .toList();

        List<Recommendation> topRecs = recommendationService.allRecommendations()
                .stream().limit(5).toList();

        return SystemAnalyticsSummary.builder()
                .totalModels(totalModels)
                .activeModels(activeModels)
                .healthyModels((int) healthy)
                .degradedModels((int) degraded)
                .unhealthyModels((int) unhealthy)
                .totalReviewsToday(totalReviewsToday)
                .totalReviewsThisMonth(totalReviewsMonth)
                .totalCostThisMonth(totalCostMonth)
                .bestPerformingModel(bestPerforming)
                .mostUsedModel(mostUsed)
                .mostCostEfficientModel(mostEfficient)
                .unresolvedAlerts(unresolvedAlerts)
                .criticalAlerts(criticalAlerts)
                .topRecommendations(topRecs)
                .build();
    }
}
