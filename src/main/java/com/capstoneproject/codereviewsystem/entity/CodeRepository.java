package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "code_repositories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String repoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepoProvider provider;

    private String accessToken;
    private String webhookId;
    private String webhookSecret;

    @Column(nullable = false)
    @Builder.Default
    private String defaultBranch = "main";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum RepoProvider {
        GITHUB, GITLAB, BITBUCKET
    }
}
