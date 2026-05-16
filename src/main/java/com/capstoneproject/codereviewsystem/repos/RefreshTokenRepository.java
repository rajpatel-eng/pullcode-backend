package com.capstoneproject.codereviewsystem.repos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;

import jakarta.transaction.Transactional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    Optional<RefreshToken> findByUser(User user);
    @Modifying
    @Transactional
    int deleteByUser(User user);
}
