package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.services.analytics.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','IAM')")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsDashboardService  dashboardService;
    private final AnalyticsAggregationService aggregationService;
    private final TrendAnalyticsService      trendService;
    private final ModelComparisonService     comparisonService;
    private final HealthMonitoringService    healthService;
    private final RecommendationService      recommendationService;
    private final AiModelRepository          modelRepository;


    @GetMapping("/summary")
    public ResponseEntity<SystemAnalyticsSummary> getSystemSummary() {
        return ResponseEntity.ok(dashboardService.getSystemSummary());
    }


    @GetMapping("/models/{modelId}/dashboard")
    public ResponseEntity<ModelDashboard> getModelDashboard(@PathVariable Long modelId) {
        return ResponseEntity.ok(dashboardService.getModelDashboard(modelId));
    }

    @GetMapping("/models/{modelId}/usage")
    public ResponseEntity<UsageMetrics> getUsageMetrics(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(aggregationService.computeUsageMetrics(model));
    }

    @GetMapping("/models/{modelId}/performance")
    public ResponseEntity<PerformanceMetrics> getPerformanceMetrics(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(aggregationService.computePerformanceMetrics(model));
    }

    @GetMapping("/models/{modelId}/quality")
    public ResponseEntity<QualityMetrics> getQualityMetrics(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(aggregationService.computeQualityMetrics(model));
    }

    @GetMapping("/models/{modelId}/cost")
    public ResponseEntity<CostMetrics> getCostMetrics(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(aggregationService.computeCostMetrics(model));
    }

    @GetMapping("/models/{modelId}/adoption")
    public ResponseEntity<AdoptionMetrics> getAdoptionMetrics(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        long total = modelRepository.findByActiveTrueAndDeletedFalse().size();
        return ResponseEntity.ok(aggregationService.computeAdoptionMetrics(model, total));
    }

    @GetMapping("/models/{modelId}/trends/usage")
    public ResponseEntity<TrendData> getUsageTrend(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "DAYS_30") TrendPeriod period) {
        return ResponseEntity.ok(trendService.usageTrend(modelId, period));
    }


    @GetMapping("/models/{modelId}/trends/cost")
    public ResponseEntity<TrendData> getCostTrend(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "DAYS_30") TrendPeriod period) {
        return ResponseEntity.ok(trendService.costTrend(modelId, period));
    }

    @GetMapping("/models/{modelId}/trends/performance")
    public ResponseEntity<TrendData> getPerformanceTrend(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "DAYS_30") TrendPeriod period) {
        return ResponseEntity.ok(trendService.performanceTrend(modelId, period));
    }


    @GetMapping("/models/{modelId}/trends/errors")
    public ResponseEntity<TrendData> getErrorTrend(
            @PathVariable Long modelId,
            @RequestParam(defaultValue = "DAYS_30") TrendPeriod period) {
        return ResponseEntity.ok(trendService.errorTrend(modelId, period));
    }

    @PostMapping("/compare")
    public ResponseEntity<ComparisonResponse> compareModels(
            @RequestBody Map<String, List<Long>> body,
            @RequestParam(defaultValue = "DAYS_30") TrendPeriod period) {
        List<Long> modelIds = body.get("modelIds");
        return ResponseEntity.ok(comparisonService.compare(modelIds, period));
    }

    @GetMapping("/models/{modelId}/health")
    public ResponseEntity<HealthStatusResponse> getHealthStatus(@PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(healthService.getHealthStatus(model));
    }

    @GetMapping("/health")
    public ResponseEntity<List<HealthStatusResponse>> getAllHealthStatuses() {
        List<HealthStatusResponse> statuses = modelRepository
                .findByActiveTrueAndDeletedFalse()
                .stream()
                .map(healthService::getHealthStatus)
                .toList();
        return ResponseEntity.ok(statuses);
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<AlertSummary>> getAllAlerts() {
        return ResponseEntity.ok(healthService.getAllUnresolvedAlerts());
    }

    @GetMapping("/models/{modelId}/alerts")
    public ResponseEntity<List<AlertSummary>> getModelAlerts(@PathVariable Long modelId) {
        return ResponseEntity.ok(healthService.getAlertsForModel(modelId));
    }

    @PostMapping("/alerts/{alertId}/resolve")
    public ResponseEntity<?> resolveAlert(@PathVariable Long alertId) {
        healthService.resolveAlert(alertId);
        return ResponseEntity.ok(Map.of("message", "Alert resolved"));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<Recommendation>> getAllRecommendations() {
        return ResponseEntity.ok(recommendationService.allRecommendations());
    }

    @GetMapping("/models/{modelId}/recommendations")
    public ResponseEntity<List<Recommendation>> getModelRecommendations(
            @PathVariable Long modelId) {
        AiModel model = aggregationService.requireModel(modelId);
        return ResponseEntity.ok(recommendationService.recommendationsForModel(model));
    }
}