package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.CliToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CliTokenRepository extends JpaRepository<CliToken, Long> {

    Optional<CliToken> findByTokenAndActiveTrue(String token);

    List<CliToken> findByZipProjectAndUserOrderByCreatedAtDesc(ZipProject project, User user);

    List<CliToken> findByZipProjectAndActiveTrueOrderByCreatedAtDesc(ZipProject project);

    Optional<CliToken> findByIdAndZipProjectAndUser(Long id, ZipProject project, User user);
}