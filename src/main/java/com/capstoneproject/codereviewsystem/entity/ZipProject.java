package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zip_projects")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZipProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private String latestStoragePath;

    @Builder.Default
    private Integer commitCount = 0;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "zipProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProjectCommit> commits = new ArrayList<>();

    @OneToMany(mappedBy = "zipProject", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CliToken> cliTokens = new ArrayList<>();
}