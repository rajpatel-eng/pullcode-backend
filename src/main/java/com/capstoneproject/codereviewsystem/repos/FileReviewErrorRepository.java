package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.FileReview;
import com.capstoneproject.codereviewsystem.entity.FileReviewError;
import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileReviewErrorRepository extends JpaRepository<FileReviewError, Long> {


    List<FileReviewError> findByFileReview(FileReview fileReview);

    long countByFileReview(FileReview fileReview);

    List<FileReviewError> findByCommitHistoryOrderBySeverityAscLineAsc(
            CommitHistory commitHistory);

    List<FileReviewError> findByProjectCommitOrderBySeverityAscLineAsc(
            ProjectCommit projectCommit);

    @Query("""
        SELECT e.severity, COUNT(e) FROM FileReviewError e
        WHERE e.commitHistory.id = :commitHistoryId
        GROUP BY e.severity
        """)
    List<Object[]> countBySeverityForCommitHistory(
            @Param("commitHistoryId") Long commitHistoryId);

    @Query("""
        SELECT e.severity, COUNT(e) FROM FileReviewError e
        WHERE e.projectCommit.id = :projectCommitId
        GROUP BY e.severity
        """)
    List<Object[]> countBySeverityForProjectCommit(
            @Param("projectCommitId") Long projectCommitId);
}
