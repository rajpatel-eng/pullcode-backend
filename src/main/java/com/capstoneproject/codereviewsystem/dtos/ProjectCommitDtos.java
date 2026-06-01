package com.capstoneproject.codereviewsystem.dtos;

import lombok.*;
import java.time.LocalDateTime;

public class ProjectCommitDtos {

    @Data
    public static class ZipUploadRequest {
        private Long tokenId;
        private String commitMessage;
        private String extraMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitResponse {
        private Long id;
        private String commitHash;
        private String commitMessage;
        private String extraMessage;
        private String originalFileName;
        private Integer totalFilesExtracted;
        private Long fileSizeBytes;
        private String storagePath;
        private String source;          
        private String reviewStatus;
        private Long projectId;
        private String projectTitle;
        private String tokenName;
        private LocalDateTime committedAt;
        private String message;         
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitHistoryItem {
        private Long id;
        private String commitHash;
        private String commitMessage;
        private String extraMessage;
        private String originalFileName;
        private Long fileSizeBytes;
        private Integer totalFilesExtracted;
        private String source;         
        private String tokenName;
        private String reviewStatus;
        private LocalDateTime committedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserCommitItem {
        private Long id;
        private String source;          
        private String commitHash;
        private String commitMessage;
        private String reviewStatus;
        private LocalDateTime committedAt;

        private Long projectId;
        private String projectTitle;
        private String tokenName;
        private String originalFileName;
        private Integer totalFilesExtracted;

        private Long repositoryId;
        private String repositoryTitle;
        private String repoUrl;
        private String branch;
        private String authorName;
        private Integer filesAddedCount;
        private Integer filesModifiedCount;
        private Integer filesRemovedCount;
    }
}