package com.capstoneproject.codereviewsystem.repos;

import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
             SELECT DISTINCT u
             FROM User u
             JOIN u.roles r
             WHERE r = :role
            """)
    Page<User> findByRole(@Param("role") Role role, Pageable pageable);
}