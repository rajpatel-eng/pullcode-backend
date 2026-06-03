package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot;
import com.capstoneproject.codereviewsystem.entity.AiModelDailySnapshot.ModelHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelDailySnapshotRepository extends JpaRepository<AiModelDailySnapshot, Long> {

    Optional<AiModelDailySnapshot> findByAiModelAndSnapshotDate(
            AiModel aiModel, LocalDate snapshotDate);

    List<AiModelDailySnapshot> findByAiModelAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            AiModel aiModel, LocalDate from, LocalDate to);

    Optional<AiModelDailySnapshot> findTopByAiModelOrderBySnapshotDateDesc(AiModel aiModel);

    List<AiModelDailySnapshot> findBySnapshotDate(LocalDate date);

    @Query("""
        SELECT s FROM AiModelDailySnapshot s
        WHERE s.snapshotDate = (
            SELECT MAX(s2.snapshotDate) FROM AiModelDailySnapshot s2
            WHERE s2.aiModel = s.aiModel
        )
        AND s.healthStatus = :status
        """)
    List<AiModelDailySnapshot> findLatestByHealthStatus(
            @Param("status") ModelHealthStatus status);

    List<AiModelDailySnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(
            LocalDate from, LocalDate to);
}