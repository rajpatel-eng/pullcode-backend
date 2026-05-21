package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.User;

import io.lettuce.core.dynamic.annotation.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, Long> {

    List<CodeRepository> findByUser(User user);

    Optional<CodeRepository> findByIdAndUser(Long id, User user);

    boolean existsByRepoUrlAndUser(String repoUrl, User user);

    @Query("SELECT r FROM CodeRepository r WHERE :url LIKE CONCAT('%', r.repoUrl, '%') OR r.repoUrl LIKE CONCAT('%', :url, '%')")
    Optional<CodeRepository> findByRepoUrlContaining(@Param("url") String url);
}

