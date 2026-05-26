package com.capstoneproject.codereviewsystem.dtos;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;


public class ZipProjectDtos {

    @Data
    public static class CreateProjectRequest {
        private String title;
        private String description;
    }

    @Data
    public static class UpdateProjectRequest {
        private String title;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectResponse {
        private Long id;
        private String title;
        private String description;
        private Integer uploadCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String latestUploadSubtitle;
        private LocalDateTime latestUploadAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadHistoryResponse {
        private Long id;
        private String subtitle;
        private String originalFileName;
        private Long fileSizeBytes;
        private Integer totalFilesExtracted;
        private String commitMessage;   // NEW — commit-style message
        private String extraMessage;    // AI context / focus hints
        private String storagePath;
        private String reviewStatus;
        private LocalDateTime uploadedAt;
        private Long projectId;
        private String projectTitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadResponse {
        private Long historyId;
        private String subtitle;
        private String originalFileName;
        private Integer totalFilesExtracted;
        private String storagePath;
        private String reviewStatus;
        private String message;
        private LocalDateTime uploadedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectWithHistoryResponse {
        private ProjectResponse project;
        private List<UploadHistoryResponse> uploadHistory;
    }
}