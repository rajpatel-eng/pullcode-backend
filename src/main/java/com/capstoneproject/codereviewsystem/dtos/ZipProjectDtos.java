package com.capstoneproject.codereviewsystem.dtos;

import lombok.*;
import java.time.LocalDateTime;

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
        private Integer commitCount;          
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String latestCommitMessage;   
        private LocalDateTime latestCommitAt;
    }
}