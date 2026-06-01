package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;


@Entity
@Table(name = "project_commits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String commitHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commitMessage;

    @Column(columnDefinition = "TEXT")
    private String extraMessage;

    @Column(nullable = false)
    private String originalFileName;

    private Long fileSizeBytes;

    private Integer totalFilesExtracted;

    @Column(nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    private String pusherHostname;

    private String pusherOsUser;

    @Column(nullable = false)
    private LocalDateTime committedAt;

    @PrePersist
    protected void onCreate() {
        committedAt = LocalDateTime.now();
    }


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zip_project_id", nullable = false)
    private ZipProject zipProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cli_token_id", nullable = false)
    private CliToken cliToken;


    public enum Source {
        ZIP_UI, CLI
    }

    public enum ReviewStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}