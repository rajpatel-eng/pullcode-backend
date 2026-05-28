package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.entity.ZipUploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZipUploadHistoryRepository extends JpaRepository<ZipUploadHistory, Long> {

    List<ZipUploadHistory> findByZipProjectOrderByUploadedAtDesc(ZipProject zipProject);

    Optional<ZipUploadHistory> findByIdAndZipProject(Long id, ZipProject zipProject);

    int countByZipProject(ZipProject zipProject);

    Optional<ZipUploadHistory> findTopByZipProjectOrderByUploadedAtDesc(ZipProject zipProject);

    @Query("SELECT COUNT(z) FROM ZipUploadHistory z WHERE z.zipProject.user = :user")
    long countByUser(@Param("user") User user);
}
