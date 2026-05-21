package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, Long> {

    List<CodeRepository> findByUser(User user);

    Optional<CodeRepository> findByIdAndUser(Long id, User user);

    boolean existsByRepoUrlAndUser(String repoUrl, User user);
}