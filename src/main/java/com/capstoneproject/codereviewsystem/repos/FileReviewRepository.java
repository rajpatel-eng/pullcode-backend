package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.FileReview;
import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileReviewRepository extends JpaRepository<FileReview, Long> {

    List<FileReview> findByCommitHistoryOrderByFilePath(CommitHistory commitHistory);

    List<FileReview> findByProjectCommitOrderByFilePath(ProjectCommit projectCommit);

    @Query("""
        SELECT fr FROM FileReview fr
        WHERE fr.commitHistory.repository.id = :repoId
          AND fr.filePath = :filePath
        ORDER BY fr.createdAt DESC
        LIMIT 1
        """)
    Optional<FileReview> findTopByRepoAndFilePathOrderByCreatedAtDesc(
            @Param("repoId") Long repoId,
            @Param("filePath") String filePath);

    @Query("""
        SELECT fr FROM FileReview fr
        WHERE fr.projectCommit.zipProject.id = :projectId
          AND fr.filePath = :filePath
        ORDER BY fr.createdAt DESC
        LIMIT 1
        """)
    Optional<FileReview> findTopByProjectAndFilePathOrderByCreatedAtDesc(
            @Param("projectId") Long projectId,
            @Param("filePath") String filePath);

    @Query("""
        SELECT fr FROM FileReview fr
        WHERE fr.commitHistory.repository.id = :repoId
          AND fr.filePath = :filePath
          AND fr.sentToAi = true
        ORDER BY fr.createdAt DESC
        LIMIT 1
        """)
    Optional<FileReview> findLatestReviewedByRepoAndFilePath(
            @Param("repoId") Long repoId,
            @Param("filePath") String filePath);

    @Query("""
        SELECT fr FROM FileReview fr
        WHERE fr.projectCommit.zipProject.id = :projectId
          AND fr.filePath = :filePath
          AND fr.sentToAi = true
        ORDER BY fr.createdAt DESC
        LIMIT 1
        """)
    Optional<FileReview> findLatestReviewedByProjectAndFilePath(
            @Param("projectId") Long projectId,
            @Param("filePath") String filePath);
}
