package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "zip_upload_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZipUploadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subtitle;

    @Column(nullable = false)
    private String originalFileName;

    private Long fileSizeBytes;

    private Integer totalFilesExtracted;

    @Column(columnDefinition = "TEXT")
    private String commitMessage;

    @Column(columnDefinition = "TEXT")
    private String extraMessage;

    @Column(nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zip_project_id", nullable = false)
    private ZipProject zipProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public enum ReviewStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
}