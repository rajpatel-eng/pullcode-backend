package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cli_commit_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CliCommitHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String commitHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commitMessage;

    @Column(nullable = false)
    private String originalFileName;

    private Long fileSizeBytes;

    private Integer totalFilesExtracted;

    @Column(nullable = false)
    private String storagePath;

    private String pusherHostname;

    private String pusherOsUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zip_project_id", nullable = false)
    private ZipProject zipProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cli_token_id", nullable = false)
    private CliToken cliToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime pushedAt;

    @PrePersist
    protected void onCreate() {
        pushedAt = LocalDateTime.now();
    }

    public enum ReviewStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
