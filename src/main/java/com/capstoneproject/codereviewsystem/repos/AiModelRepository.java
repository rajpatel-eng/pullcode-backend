package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    Optional<AiModel> findByDefaultModelTrueAndDeletedFalse();

    Page<AiModel> findByDeletedFalse(Pageable pageable);

    List<AiModel> findByActiveTrueAndDeletedFalse();

    Optional<AiModel> findByIdAndDeletedFalse(Long id);

    Optional<AiModel> findByIdAndActiveTrueAndDeletedFalse(Long id);

    boolean existsByDefaultModelTrueAndDeletedFalse();

    @Query("SELECT COUNT(m) FROM AiModel m WHERE m.defaultModel = true AND m.deleted = false")
    long countDefaultModels();
}