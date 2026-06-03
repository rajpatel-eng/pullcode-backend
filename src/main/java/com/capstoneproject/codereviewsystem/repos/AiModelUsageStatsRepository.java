package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelUsageStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelUsageStatsRepository extends JpaRepository<AiModelUsageStats, Long> {

    Optional<AiModelUsageStats> findByAiModelAndStatDate(AiModel aiModel, LocalDate statDate);

    List<AiModelUsageStats> findByAiModelAndStatDateBetweenOrderByStatDateAsc(
            AiModel aiModel, LocalDate from, LocalDate to);

    List<AiModelUsageStats> findByStatDateBetweenOrderByStatDateAsc(
            LocalDate from, LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(s.totalReviews), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model
          AND s.statDate BETWEEN :from AND :to
        """)
    long sumTotalReviews(@Param("model") AiModel model,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(s.totalTokens), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model
          AND s.statDate BETWEEN :from AND :to
        """)
    long sumTotalTokens(@Param("model") AiModel model,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(s.totalCost), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model
          AND s.statDate BETWEEN :from AND :to
        """)
    BigDecimal sumTotalCost(@Param("model") AiModel model,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(s.successCount), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model
          AND s.statDate BETWEEN :from AND :to
        """)
    long sumSuccessCount(@Param("model") AiModel model,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(s.failureCount), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model
          AND s.statDate BETWEEN :from AND :to
        """)
    long sumFailureCount(@Param("model") AiModel model,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    @Query("""
        SELECT s.aiModel.id, COALESCE(SUM(s.totalReviews), 0)
        FROM AiModelUsageStats s
        WHERE s.statDate BETWEEN :from AND :to
        GROUP BY s.aiModel.id
        """)
    List<Object[]> sumReviewsByModelBetween(@Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
        SELECT s.aiModel.id, COALESCE(SUM(s.totalCost), 0)
        FROM AiModelUsageStats s
        WHERE s.statDate BETWEEN :from AND :to
        GROUP BY s.aiModel.id
        """)
    List<Object[]> sumCostByModelBetween(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to);


    @Query("""
        SELECT COALESCE(SUM(s.totalReviews), 0)
        FROM AiModelUsageStats s
        WHERE s.aiModel = :model AND s.statDate = :date
        """)
    long sumReviewsOnDate(@Param("model") AiModel model, @Param("date") LocalDate date);
}