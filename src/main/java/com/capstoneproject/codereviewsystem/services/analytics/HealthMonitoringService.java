package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.entity.*;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertSeverity;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertType;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord.ReviewOutcome;
import com.capstoneproject.codereviewsystem.repos.*;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthMonitoringService {

        private final AiModelRepository modelRepository;
        private final AiModelReviewRecordRepository reviewRecordRepo;
        private final AiModelHealthAlertRepository alertRepository;
        private final AiModelUsageStatsRepository usageStatsRepo;
        private final UserRepository userRepository;
        private final EmailService emailService;
        private final EmailContentService emailContentService;

        // Configurable thresholds (can be moved to application.yml)
        @Value("${app.analytics.threshold.success-rate:80.0}")
        private double successRateThreshold;

        @Value("${app.analytics.threshold.error-rate:15.0}")
        private double errorRateThreshold;

        @Value("${app.analytics.threshold.latency-ms:10000}")
        private double latencyThresholdMs;

        @Value("${app.analytics.threshold.cost-spike-multiplier:3.0}")
        private double costSpikeMultiplier;

        @Value("${app.analytics.threshold.rate-limit-count:20}")
        private int rateLimitThreshold;

        @Value("${app.analytics.threshold.timeout-count:20}")
        private int timeoutThreshold;

        // ── Health Status for a single model ─────────────────────────────────────

        public HealthStatusResponse getHealthStatus(AiModel model) {
                LocalDateTime since = LocalDateTime.now().minusHours(1);
                List<AiModelReviewRecord> recent = reviewRecordRepo.findRecentByModel(model, since);

                long total = recent.size();
                long success = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.SUCCESS).count();
                long errors = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.FAILED).count();
                long timeouts = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.TIMEOUT).count();
                long rateLimits = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.RATE_LIMITED).count();

                double successRate = total > 0 ? (success * 100.0 / total) : 100.0;
                double errorRate = total > 0 ? (errors * 100.0 / total) : 0.0;
                double avgLatency = recent.stream()
                                .filter(r -> r.getLatencyMs() != null)
                                .mapToLong(AiModelReviewRecord::getLatencyMs)
                                .average().orElse(0.0);

                ModelHealthStatus status = computeStatus(successRate, errorRate, avgLatency);

                List<AiModelHealthAlert> activeAlerts = alertRepository
                                .findByAiModelAndResolvedFalseOrderByCreatedAtDesc(model);

                return HealthStatusResponse.builder()
                                .aiModelId(model.getId())
                                .modelName(model.getName())
                                .status(status)
                                .successRateLast1h(round2(successRate))
                                .avgLatencyLast1h(round2(avgLatency))
                                .errorRateLast1h(round2(errorRate))
                                .timeoutsLast1h(timeouts)
                                .rateLimitsLast1h(rateLimits)
                                .successRateThreshold(successRateThreshold)
                                .latencyThresholdMs(latencyThresholdMs)
                                .errorRateThreshold(errorRateThreshold)
                                .activeAlerts(activeAlerts.stream().map(this::toAlertSummary).toList())
                                .lastCheckedAt(LocalDateTime.now())
                                .build();
        }

        // ── Evaluate thresholds and create alerts ────────────────────────────────

        @Transactional
        public void evaluateAndAlert(AiModel model) {
                LocalDateTime since = LocalDateTime.now().minusHours(1);
                List<AiModelReviewRecord> recent = reviewRecordRepo.findRecentByModel(model, since);

                if (recent.isEmpty())
                        return;

                long total = recent.size();
                long success = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.SUCCESS).count();
                long errors = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.FAILED).count();
                long timeouts = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.TIMEOUT).count();
                long rateLimits = recent.stream().filter(r -> r.getOutcome() == ReviewOutcome.RATE_LIMITED).count();

                double successRate = success * 100.0 / total;
                double errorRate = errors * 100.0 / total;
                double avgLatency = recent.stream()
                                .filter(r -> r.getLatencyMs() != null)
                                .mapToLong(AiModelReviewRecord::getLatencyMs)
                                .average().orElse(0.0);

                List<AiModelHealthAlert> newAlerts = new ArrayList<>();

                // Success rate alert
                if (successRate < successRateThreshold) {
                        newAlerts.add(createAlert(model,
                                        AlertType.SUCCESS_RATE_LOW,
                                        successRate < 50.0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                                        String.format("Success rate dropped to %.1f%% (threshold: %.0f%%)",
                                                        successRate, successRateThreshold),
                                        String.valueOf(Math.round(successRate)),
                                        String.valueOf(Math.round(successRateThreshold))));
                }

                // Error rate alert
                if (errorRate > errorRateThreshold) {
                        newAlerts.add(createAlert(model,
                                        AlertType.ERROR_RATE_HIGH,
                                        errorRate > 30.0 ? AlertSeverity.CRITICAL : AlertSeverity.WARNING,
                                        String.format("Error rate is %.1f%% (threshold: %.0f%%)",
                                                        errorRate, errorRateThreshold),
                                        String.valueOf(Math.round(errorRate)),
                                        String.valueOf(Math.round(errorRateThreshold))));
                }

                // Latency alert
                if (avgLatency > latencyThresholdMs) {
                        newAlerts.add(createAlert(model,
                                        AlertType.LATENCY_HIGH,
                                        AlertSeverity.WARNING,
                                        String.format("Average latency is %.0fms (threshold: %.0fms)",
                                                        avgLatency, latencyThresholdMs),
                                        String.valueOf(Math.round(avgLatency)),
                                        String.valueOf(Math.round(latencyThresholdMs))));
                }

                // Timeout alert
                if (timeouts >= timeoutThreshold) {
                        newAlerts.add(createAlert(model,
                                        AlertType.TIMEOUT_HIGH,
                                        AlertSeverity.WARNING,
                                        String.format("%d timeouts in the last hour (threshold: %d)",
                                                        timeouts, timeoutThreshold),
                                        String.valueOf(timeouts),
                                        String.valueOf(timeoutThreshold)));
                }

                // Rate limit alert
                if (rateLimits >= rateLimitThreshold) {
                        newAlerts.add(createAlert(model,
                                        AlertType.RATE_LIMIT_HIGH,
                                        AlertSeverity.WARNING,
                                        String.format("%d rate-limit errors in the last hour (threshold: %d)",
                                                        rateLimits, rateLimitThreshold),
                                        String.valueOf(rateLimits),
                                        String.valueOf(rateLimitThreshold)));
                }

                // Cost spike alert (compare today vs 7-day average)
                checkCostSpike(model).ifPresent(newAlerts::add);

                // Save new alerts and notify
                for (AiModelHealthAlert alert : newAlerts) {
                        alertRepository.save(alert);
                        sendAlertNotification(model, alert);
                }

                // Auto-resolve alerts that are no longer firing
                autoResolveAlerts(model, newAlerts);
        }

        // ── List all alerts ───────────────────────────────────────────────────────

        public List<AlertSummary> getAllUnresolvedAlerts() {
                return alertRepository.findByResolvedFalseOrderByCreatedAtDesc()
                                .stream().map(this::toAlertSummary).toList();
        }

        public List<AlertSummary> getAlertsForModel(Long modelId) {
                AiModel model = modelRepository.findByIdAndDeletedFalse(modelId)
                                .orElseThrow(() -> new com.capstoneproject.codereviewsystem.exceptions.BadRequestException(
                                                "Model not found"));
                return alertRepository.findByAiModelAndResolvedFalseOrderByCreatedAtDesc(model)
                                .stream().map(this::toAlertSummary).toList();
        }

        @Transactional
        public void resolveAlert(Long alertId) {
                alertRepository.findById(alertId).ifPresent(a -> {
                        a.setResolved(true);
                        a.setResolvedAt(LocalDateTime.now());
                        alertRepository.save(a);
                });
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private ModelHealthStatus computeStatus(double successRate,
                        double errorRate,
                        double avgLatency) {
                if (successRate >= 95.0 && errorRate < 5.0 && avgLatency < latencyThresholdMs)
                        return ModelHealthStatus.HEALTHY;
                if (successRate >= 80.0 && errorRate < 15.0)
                        return ModelHealthStatus.DEGRADED;
                if (successRate >= 50.0)
                        return ModelHealthStatus.UNHEALTHY;
                return ModelHealthStatus.OFFLINE;
        }

        private AiModelHealthAlert createAlert(AiModel model, AlertType type,
                        AlertSeverity severity, String message,
                        String triggerValue, String thresholdValue) {
                // Deduplicate: skip if same unresolved alert already exists
                if (alertRepository.existsByAiModelAndAlertTypeAndResolvedFalse(model, type)) {
                        return null;
                }
                return AiModelHealthAlert.builder()
                                .aiModel(model)
                                .alertType(type)
                                .severity(severity)
                                .message(message)
                                .triggerValue(triggerValue)
                                .thresholdValue(thresholdValue)
                                .resolved(false)
                                .notified(false)
                                .build();
        }

        private java.util.Optional<AiModelHealthAlert> checkCostSpike(AiModel model) {
                java.time.LocalDate today = java.time.LocalDate.now();
                java.time.LocalDate weekAgo = today.minusDays(7);

                var todayStat = usageStatsRepo.findByAiModelAndStatDate(model, today);
                var weekStats = usageStatsRepo.findByAiModelAndStatDateBetweenOrderByStatDateAsc(
                                model, weekAgo, today.minusDays(1));

                if (todayStat.isEmpty() || weekStats.isEmpty())
                        return java.util.Optional.empty();

                BigDecimal todayCost = todayStat.get().getTotalCost();
                double avgDaily = weekStats.stream()
                                .mapToDouble(s -> s.getTotalCost().doubleValue())
                                .average().orElse(0.0);

                if (avgDaily == 0 || todayCost.doubleValue() < avgDaily)
                        return java.util.Optional.empty();

                double spike = todayCost.doubleValue() / avgDaily;
                if (spike < costSpikeMultiplier)
                        return java.util.Optional.empty();

                if (alertRepository.existsByAiModelAndAlertTypeAndResolvedFalse(
                                model, AlertType.COST_SPIKE))
                        return java.util.Optional.empty();

                return java.util.Optional.of(AiModelHealthAlert.builder()
                                .aiModel(model)
                                .alertType(AlertType.COST_SPIKE)
                                .severity(AlertSeverity.WARNING)
                                .message(String.format("Today's cost ($%.4f) is %.1fx the 7-day average ($%.4f)",
                                                todayCost.doubleValue(), spike, avgDaily))
                                .triggerValue(String.valueOf(Math.round(todayCost.doubleValue() * 10000.0) / 10000.0))
                                .thresholdValue(String.format("%.4f (%.1fx avg)", avgDaily * costSpikeMultiplier,
                                                costSpikeMultiplier))
                                .resolved(false)
                                .notified(false)
                                .build());
        }

        private void autoResolveAlerts(AiModel model,
                        List<AiModelHealthAlert> currentAlerts) {
                List<AlertType> firingTypes = currentAlerts.stream()
                                .filter(a -> a != null)
                                .map(AiModelHealthAlert::getAlertType).toList();

                alertRepository.findByAiModelAndResolvedFalseOrderByCreatedAtDesc(model)
                                .stream()
                                .filter(a -> !firingTypes.contains(a.getAlertType()))
                                .forEach(a -> {
                                        a.setResolved(true);
                                        a.setResolvedAt(LocalDateTime.now());
                                        alertRepository.save(a);
                                        log.info("Auto-resolved alert {} for model {}", a.getAlertType(),
                                                        model.getName());
                                });
        }

        @Async
        void sendAlertNotification(AiModel model, AiModelHealthAlert alert) {
                if (alert == null || alert.isNotified())
                        return;
                try {
                        // Notify all ADMIN and IAM users
                        userRepository.findByRole(
                                        Role.ROLE_ADMIN,
                                        PageRequest.of(0, 50))
                                        .forEach(u -> emailService.sendEmail(
                                                        u.getEmail(),
                                                        emailContentService.healthAlertSubject(model.getName(),
                                                                        alert.getAlertType().name()),
                                                        emailContentService.healthAlertBody(u.getName(),
                                                                        model.getName(), alert.getMessage())));
                        userRepository.findByRole(
                                        Role.ROLE_IAM,
                                        PageRequest.of(0, 50))
                                        .forEach(u -> emailService.sendEmail(
                                                        u.getEmail(),
                                                        emailContentService.healthAlertSubject(model.getName(),
                                                                        alert.getAlertType().name()),
                                                        emailContentService.healthAlertBody(u.getName(),
                                                                        model.getName(), alert.getMessage())));
                        alert.setNotified(true);
                        alertRepository.save(alert);
                } catch (Exception e) {
                        log.error("Failed to send alert notification: {}", e.getMessage());
                }
        }

        private AlertSummary toAlertSummary(AiModelHealthAlert a) {
                return AlertSummary.builder()
                                .id(a.getId())
                                .aiModelId(a.getAiModel().getId())
                                .modelName(a.getAiModel().getName())
                                .alertType(a.getAlertType())
                                .severity(a.getSeverity())
                                .message(a.getMessage())
                                .triggerValue(a.getTriggerValue())
                                .thresholdValue(a.getThresholdValue())
                                .resolved(a.isResolved())
                                .createdAt(a.getCreatedAt())
                                .build();
        }

        private double round2(double v) {
                return Math.round(v * 100.0) / 100.0;
        }
}
