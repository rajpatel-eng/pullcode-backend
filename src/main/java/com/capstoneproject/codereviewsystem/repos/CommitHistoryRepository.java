package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.CommitHistory.ReviewStatus;
import com.capstoneproject.codereviewsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitHistoryRepository extends JpaRepository<CommitHistory, Long> {

        Page<CommitHistory> findByRepositoryOrderByCommittedAtDesc(
                        CodeRepository repository, Pageable pageable);

        Page<CommitHistory> findByUserOrderByCommittedAtDesc(
                        User user, Pageable pageable);

        Page<CommitHistory> findByRepositoryAndBranchOrderByCommittedAtDesc(
                        CodeRepository repository, String branch, Pageable pageable);

        List<CommitHistory> findByReviewStatus(ReviewStatus status);

        Optional<CommitHistory> findByCommitIdAndRepository(
                        String commitId, CodeRepository repository);

        Optional<CommitHistory> findTopByRepositoryOrderByReceivedAtDesc(CodeRepository repository);
}
