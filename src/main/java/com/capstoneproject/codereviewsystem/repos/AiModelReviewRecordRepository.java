package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord.ReviewOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiModelReviewRecordRepository extends JpaRepository<AiModelReviewRecord, Long> {

  @Query("""
      SELECT r.latencyMs FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.reviewedAt BETWEEN :from AND :to
        AND r.latencyMs IS NOT NULL
      ORDER BY r.latencyMs ASC
      """)
  List<Long> findLatenciesBetween(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COUNT(r) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  long countByModelAndDateRange(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COUNT(r) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.outcome = :outcome
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  long countByModelOutcomeAndDateRange(@Param("model") AiModel model,
      @Param("outcome") ReviewOutcome outcome,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COALESCE(AVG(r.latencyMs), 0) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.reviewedAt BETWEEN :from AND :to
        AND r.latencyMs IS NOT NULL
      """)
  Double avgLatencyByModelAndDateRange(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COALESCE(AVG(r.reviewScore), 0) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.reviewScore IS NOT NULL
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  Double avgReviewScoreByModel(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COALESCE(AVG(r.userRating), 0) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.userRating IS NOT NULL
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  Double avgUserRatingByModel(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COUNT(r) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.falsePositive = true
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  long countFalsePositives(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT COUNT(r) FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.falseNegative = true
        AND r.reviewedAt BETWEEN :from AND :to
      """)
  long countFalseNegatives(@Param("model") AiModel model,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT r FROM AiModelReviewRecord r
      WHERE r.aiModel = :model
        AND r.reviewedAt >= :since
      ORDER BY r.reviewedAt DESC
      """)
  List<AiModelReviewRecord> findRecentByModel(@Param("model") AiModel model,
      @Param("since") LocalDateTime since);
}