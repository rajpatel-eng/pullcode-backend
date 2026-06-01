package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectCommitRepository extends JpaRepository<ProjectCommit, Long> {

    Page<ProjectCommit> findByZipProjectIdOrderByCommittedAtDesc(Long projectId, Pageable pageable);

    Page<ProjectCommit> findByZipProjectIdAndUserIdOrderByCommittedAtDesc(
            Long projectId, Long userId, Pageable pageable);

    Page<ProjectCommit> findByUserIdOrderByCommittedAtDesc(Long userId, Pageable pageable);

    long countByZipProjectId(Long projectId);

    @Query("SELECT COUNT(c) FROM ProjectCommit c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}