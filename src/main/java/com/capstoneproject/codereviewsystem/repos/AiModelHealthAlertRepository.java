package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert;
import com.capstoneproject.codereviewsystem.entity.AiModelHealthAlert.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiModelHealthAlertRepository extends JpaRepository<AiModelHealthAlert, Long> {

    List<AiModelHealthAlert> findByResolvedFalseOrderByCreatedAtDesc();

    List<AiModelHealthAlert> findByAiModelAndResolvedFalseOrderByCreatedAtDesc(AiModel aiModel);

    Page<AiModelHealthAlert> findByAiModelOrderByCreatedAtDesc(AiModel aiModel, Pageable pageable);

    Page<AiModelHealthAlert> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByAiModelAndAlertTypeAndResolvedFalse(AiModel aiModel, AlertType alertType);

    @Query("""
        SELECT COUNT(a) FROM AiModelHealthAlert a
        WHERE a.resolved = false
          AND a.createdAt >= :since
        """)
    long countUnresolvedSince(@Param("since") LocalDateTime since);

    List<AiModelHealthAlert> findByResolvedFalseAndCreatedAtBefore(LocalDateTime threshold);
}