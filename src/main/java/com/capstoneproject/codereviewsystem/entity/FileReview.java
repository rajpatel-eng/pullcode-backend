package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "file_reviews",
        indexes = {
            @Index(name = "idx_fr_commit_history",  columnList = "commit_history_id"),
            @Index(name = "idx_fr_project_commit",  columnList = "project_commit_id"),
            @Index(name = "idx_fr_file_path",       columnList = "file_path"),
            @Index(name = "idx_fr_file_hash",       columnList = "file_hash")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_history_id")
    private CommitHistory commitHistory;         

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_commit_id")
    private ProjectCommit projectCommit;          

    @Column(nullable = false, length = 255)
    private String filePath;

    @Column(name = "file_hash", length = 64)
    private String fileHash;


    @Column(nullable = false)
    @Builder.Default
    private boolean sentToAi = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean neverReviewed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_file_review_id")
    private FileReview sourceFileReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id")
    private AiModel aiModel;

    @OneToMany(mappedBy = "fileReview", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileReviewError> errors = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
