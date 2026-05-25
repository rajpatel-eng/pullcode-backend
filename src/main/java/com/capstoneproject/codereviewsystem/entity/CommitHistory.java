package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "commit_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String commitId;

    @Column(nullable = false)
    private String commitMessage;

    @Column(nullable = false)
    private String commitUrl;

    private String authorName;
    private String authorEmail;

    @Column(nullable = false)
    private String branch;

    @Column(columnDefinition = "TEXT")
    private String filesChanged;

    private Integer filesAddedCount;
    private Integer filesModifiedCount;
    private Integer filesRemovedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private CodeRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime committedAt;

    @Column
    private String storagePath;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @PrePersist
    protected void onCreate() {
        receivedAt = LocalDateTime.now();
    }

    public enum ReviewStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}