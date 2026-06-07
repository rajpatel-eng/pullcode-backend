package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_review_errors", indexes = {
        @Index(name = "idx_fre_file_review", columnList = "file_review_id"),
        @Index(name = "idx_fre_commit_history", columnList = "commit_history_id"),
        @Index(name = "idx_fre_project_commit", columnList = "project_commit_id"),
        @Index(name = "idx_fre_severity", columnList = "severity")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReviewError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_review_id", nullable = false)
    private FileReview fileReview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commit_history_id")
    private CommitHistory commitHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_commit_id")
    private ProjectCommit projectCommit;


    @Column(name = "col_number", nullable = false)
    @Builder.Default
    private int column = 0;

    @Column(name = "line_number", nullable = false) 
    @Builder.Default
    private int line = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.MEDIUM;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "rule_id", length = 100)
    private String ruleId;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(nullable = false)
    @Builder.Default
    private boolean fresh = true;

    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO
    }
}
