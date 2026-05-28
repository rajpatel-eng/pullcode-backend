package com.capstoneproject.codereviewsystem.entity;

import java.util.HashSet;
import java.util.Set;

import com.capstoneproject.codereviewsystem.dtos.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.Role;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    private AuthProvider authProvider;

    @Column(nullable = false)
    @Builder.Default
    private boolean emailVerified = false; 

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();
}