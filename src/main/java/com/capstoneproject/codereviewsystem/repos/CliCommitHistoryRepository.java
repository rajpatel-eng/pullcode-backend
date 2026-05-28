package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CliCommitHistory;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CliCommitHistoryRepository extends JpaRepository<CliCommitHistory, Long> {

    List<CliCommitHistory> findByZipProjectOrderByPushedAtDesc(ZipProject project);

    Optional<CliCommitHistory> findByCommitHashAndZipProject(String hash, ZipProject project);

    long countByZipProject(ZipProject project);

    @Query("SELECT COUNT(c) FROM CliCommitHistory c WHERE c.zipProject.user = :user")
    long countByUser(@Param("user") User user);
}