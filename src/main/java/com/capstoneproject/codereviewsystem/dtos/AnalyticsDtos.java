package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertSeverity;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AnalyticsDtos {

    @Data
    @Builder
    public static class UsageMetrics {
        private Long aiModelId;
        private String modelName;
        private String provider;

        private long totalRepositoriesUsing;
        private long activeRepositoriesUsing;

        private long totalReviews;
        private long reviewsToday;
        private long reviewsThisWeek;
        private long reviewsThisMonth;

        private long totalTokens;
        private long inputTokens;
        private long outputTokens;
    }


    @Data
    @Builder
    public static class PerformanceMetrics {
        private Long aiModelId;
        private String modelName;

        private double avgResponseTimeMs;
        private double p95ResponseTimeMs;
        private double successRate;         
        private double failureRate;
        private long   timeoutCount;
        private long   rateLimitCount;
        private double avgReviewGenerationMs;

        private ModelHealthStatus healthStatus;
    }


    @Data
    @Builder
    public static class QualityMetrics {
        private Long aiModelId;
        private String modelName;

        private double avgReviewScore;          
        private double userFeedbackRating;      
        private double helpfulReviewPercentage; 
        private double acceptanceRate;          
        private double falsePositiveRate;
        private double falseNegativeRate;
    }

    @Data
    @Builder
    public static class CostMetrics {
        private Long aiModelId;
        private String modelName;
        private String provider;

        private BigDecimal totalCost;
        private BigDecimal costToday;
        private BigDecimal costThisMonth;
        private BigDecimal avgCostPerReview;
        private BigDecimal avgCostPerRepository;
        private BigDecimal estimatedMonthlyProjection;
    }


    @Data
    @Builder
    public static class AdoptionMetrics {
        private Long aiModelId;
        private String modelName;

        private long currentRepositories;
        private long newRepositoriesThisMonth;
        private long repositoriesMigratedAway;
        private double repositoryGrowthTrend;     
        private double marketSharePercentage;
    }

    @Data
    @Builder
    public static class ModelDashboard {
        private Long aiModelId;
        private String modelName;
        private String provider;
        private boolean active;
        private boolean defaultModel;
        private ModelHealthStatus healthStatus;

        private UsageMetrics usage;
        private PerformanceMetrics performance;
        private QualityMetrics quality;
        private CostMetrics cost;
        private AdoptionMetrics adoption;

        private List<String> recommendations;
        private List<AlertSummary> activeAlerts;
    }


    @Data
    @Builder
    public static class TrendPoint {
        private LocalDate date;
        private double value;
    }

    @Data
    @Builder
    public static class TrendData {
        private Long aiModelId;
        private String modelName;
        private String metricName;
        private String period;         
        private List<TrendPoint> points;
    }


    @Data
    @Builder
    public static class ModelComparisonRow {
        private Long aiModelId;
        private String modelName;
        private String provider;
        private boolean isDefault;
        private ModelHealthStatus healthStatus;

        private long totalReviews;
        private long repositoriesUsing;

        private BigDecimal totalCost;
        private BigDecimal avgCostPerReview;

        private double avgResponseTimeMs;
        private double p95ResponseTimeMs;
        private double successRate;

        private double avgReviewScore;
        private double userFeedbackRating;

        private double marketSharePercentage;
        private double repositoryGrowthTrend;
    }


    @Data
    @Builder
    public static class ComparisonResponse {
        private List<Long>               modelIds;
        private List<ModelComparisonRow> rows;
        private Map<String, TrendData>   usageTrends;  
        private Map<String, TrendData>   costTrends;
        private Map<String, TrendData>   performanceTrends;
        private List<String>             recommendations;
    }


    @Data
    @Builder
    public static class HealthStatusResponse {
        private Long aiModelId;
        private String modelName;
        private ModelHealthStatus status;

        private double successRateLast1h;
        private double avgLatencyLast1h;
        private double errorRateLast1h;
        private long   timeoutsLast1h;
        private long   rateLimitsLast1h;

        private double successRateThreshold;
        private double latencyThresholdMs;
        private double errorRateThreshold;

        private List<AlertSummary> activeAlerts;
        private LocalDateTime lastCheckedAt;
    }


    @Data
    @Builder
    public static class AlertSummary {
        private Long id;
        private Long aiModelId;
        private String modelName;
        private AlertType alertType;
        private AlertSeverity severity;
        private String message;
        private String triggerValue;
        private String thresholdValue;
        private boolean resolved;
        private LocalDateTime createdAt;
    }


    @Data
    @Builder
    public static class Recommendation {
        private Long aiModelId;
        private String modelName;
        private String type;        
        private String reason;      
        private String impact;      
        private int    priority;    
    }


    @Data
    @Builder
    public static class SystemAnalyticsSummary {
        private int totalModels;
        private int activeModels;
        private int healthyModels;
        private int degradedModels;
        private int unhealthyModels;

        private long totalReviewsToday;
        private long totalReviewsThisMonth;
        private BigDecimal totalCostThisMonth;

        private String bestPerformingModel;    
        private String mostUsedModel;         
        private String mostCostEfficientModel; 

        private long unresolvedAlerts;
        private List<AlertSummary> criticalAlerts;
        private List<Recommendation> topRecommendations;
    }


    public enum TrendPeriod {
        DAYS_7, DAYS_30, DAYS_90, DAYS_365;

        public int getDays() {
            return switch (this) {
                case DAYS_7   -> 7;
                case DAYS_30  -> 30;
                case DAYS_90  -> 90;
                case DAYS_365 -> 365;
            };
        }

        public String getLabel() {
            return switch (this) {
                case DAYS_7   -> "7d";
                case DAYS_30  -> "30d";
                case DAYS_90  -> "90d";
                case DAYS_365 -> "1y";
            };
        }
    }
}