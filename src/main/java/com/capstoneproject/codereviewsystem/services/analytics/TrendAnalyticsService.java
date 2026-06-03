package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.dtos.AnalyticsDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot;
import com.capstoneproject.codereviewsystem.entity.AiModelUsageStats;
import com.capstoneproject.codereviewsystem.repos.AiModelDailySnapshotRepository;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.AiModelUsageStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendAnalyticsService {

    private final AiModelDailySnapshotRepository snapshotRepo;
    private final AiModelUsageStatsRepository usageStatsRepo;
    private final AiModelRepository modelRepository;
    private final ObjectMapper objectMapper;

    // ── Single-model trends ───────────────────────────────────────────────────

    public TrendData usageTrend(Long modelId, TrendPeriod period) {
        AiModel model = requireModel(modelId);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1);

        List<AiModelUsageStats> stats = usageStatsRepo.findByAiModelAndStatDateBetweenOrderByStatDateAsc(model, from,
                to);

        Map<LocalDate, Long> byDate = stats.stream()
                .collect(Collectors.toMap(
                        AiModelUsageStats::getStatDate,
                        s -> (long) s.getTotalReviews()));

        List<TrendPoint> points = buildDateSeries(from, to, d -> byDate.getOrDefault(d, 0L).doubleValue());

        return TrendData.builder()
                .aiModelId(modelId)
                .modelName(model.getName())
                .metricName("totalReviews")
                .period(period.getLabel())
                .points(points)
                .build();
    }

    public TrendData costTrend(Long modelId, TrendPeriod period) {
        AiModel model = requireModel(modelId);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1);

        List<AiModelUsageStats> stats = usageStatsRepo.findByAiModelAndStatDateBetweenOrderByStatDateAsc(model, from,
                to);

        Map<LocalDate, Double> byDate = stats.stream()
                .collect(Collectors.toMap(AiModelUsageStats::getStatDate,
                        s -> s.getTotalCost().doubleValue()));

        List<TrendPoint> points = buildDateSeries(from, to, d -> byDate.getOrDefault(d, 0.0));

        return TrendData.builder()
                .aiModelId(modelId)
                .modelName(model.getName())
                .metricName("costUsd")
                .period(period.getLabel())
                .points(points)
                .build();
    }

    public TrendData performanceTrend(Long modelId, TrendPeriod period) {
        AiModel model = requireModel(modelId);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1);

        List<AiModelDailySnapshot> snapshots = snapshotRepo
                .findByAiModelAndSnapshotDateBetweenOrderBySnapshotDateAsc(model, from, to);

        Map<LocalDate, Double> byDate = new HashMap<>();
        for (AiModelDailySnapshot snap : snapshots) {
            double avgLatency = extractDoubleFromJson(
                    snap.getPerformanceMetricsJson(), "avgLatencyMs");
            byDate.put(snap.getSnapshotDate(), avgLatency);
        }

        List<TrendPoint> points = buildDateSeries(from, to, d -> byDate.getOrDefault(d, 0.0));

        return TrendData.builder()
                .aiModelId(modelId)
                .modelName(model.getName())
                .metricName("avgLatencyMs")
                .period(period.getLabel())
                .points(points)
                .build();
    }

    public TrendData errorTrend(Long modelId, TrendPeriod period) {
        AiModel model = requireModel(modelId);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(period.getDays() - 1);

        List<AiModelUsageStats> stats = usageStatsRepo.findByAiModelAndStatDateBetweenOrderByStatDateAsc(model, from,
                to);

        Map<LocalDate, Double> byDate = stats.stream()
                .collect(Collectors.toMap(AiModelUsageStats::getStatDate, s -> {
                    int total = s.getTotalReviews();
                    return total > 0 ? (s.getFailureCount() * 100.0 / total) : 0.0;
                }));

        List<TrendPoint> points = buildDateSeries(from, to, d -> byDate.getOrDefault(d, 0.0));

        return TrendData.builder()
                .aiModelId(modelId)
                .modelName(model.getName())
                .metricName("errorRatePct")
                .period(period.getLabel())
                .points(points)
                .build();
    }

    // ── Multi-model comparison trends ─────────────────────────────────────────

    public Map<String, TrendData> usageTrendsForModels(List<Long> modelIds, TrendPeriod period) {
        Map<String, TrendData> result = new LinkedHashMap<>();
        for (Long id : modelIds) {
            result.put(String.valueOf(id), usageTrend(id, period));
        }
        return result;
    }

    public Map<String, TrendData> costTrendsForModels(List<Long> modelIds, TrendPeriod period) {
        Map<String, TrendData> result = new LinkedHashMap<>();
        for (Long id : modelIds) {
            result.put(String.valueOf(id), costTrend(id, period));
        }
        return result;
    }

    public Map<String, TrendData> performanceTrendsForModels(List<Long> modelIds, TrendPeriod period) {
        Map<String, TrendData> result = new LinkedHashMap<>();
        for (Long id : modelIds) {
            result.put(String.valueOf(id), performanceTrend(id, period));
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface DateValueFn {
        double get(LocalDate date);
    }

    /** Fills every day in [from, to] with a value — zero-fills missing days */
    private List<TrendPoint> buildDateSeries(LocalDate from, LocalDate to, DateValueFn fn) {
        List<TrendPoint> points = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            points.add(TrendPoint.builder()
                    .date(current)
                    .value(fn.get(current))
                    .build());
            current = current.plusDays(1);
        }
        return points;
    }

    private double extractDoubleFromJson(String json, String key) {
        if (json == null || json.isBlank())
            return 0.0;
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.has(key) ? node.get(key).asDouble(0.0) : 0.0;
        } catch (Exception e) {
            log.warn("Could not parse json key '{}': {}", key, e.getMessage());
            return 0.0;
        }
    }

    private AiModel requireModel(Long id) {
        return modelRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new com.capstoneproject.codereviewsystem.exceptions.BadRequestException(
                        "AI model not found: " + id));
    }
}
