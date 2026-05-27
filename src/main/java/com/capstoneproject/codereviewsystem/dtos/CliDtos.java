package com.capstoneproject.codereviewsystem.dtos;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

public class CliDtos {


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerateTokenRequest {
        private String name; 
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CliTokenResponse {
        private Long id;
        private String token;       
        private String name;
        private Long projectId;
        private String projectTitle;
        private LocalDateTime createdAt;
        private LocalDateTime lastUsedAt;
        private boolean active;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CliPushResponse {
        private String commitHash;
        private String commitMessage;
        private String projectTitle;
        private Long projectId;
        private String originalFileName;
        private Integer totalFilesExtracted;
        private Long fileSizeBytes;
        private String storagePath;
        private String reviewStatus;
        private Long totalCommits;
        private LocalDateTime pushedAt;
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
        private String originalFileName;
        private Long fileSizeBytes;
        private Integer totalFilesExtracted;
        private String pusherHostname;
        private String pusherOsUser;
        private String reviewStatus;
        private LocalDateTime pushedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectCliHistoryResponse {
        private Long projectId;
        private String projectTitle;
        private long totalCommits;
        private List<CommitHistoryItem> commits;
    }
}
