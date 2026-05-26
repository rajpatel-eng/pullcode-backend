package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZipProjectRepository extends JpaRepository<ZipProject, Long> {

    List<ZipProject> findByUserOrderByCreatedAtDesc(User user);

    Optional<ZipProject> findByIdAndUser(Long id, User user);

    List<ZipProject> findByUserAndTitleContainingIgnoreCase(User user, String title);

    boolean existsByIdAndUser(Long id, User user);
}
