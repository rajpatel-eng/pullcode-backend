package com.capstoneproject.codereviewsystem.dtos;

import lombok.*;
import java.time.LocalDateTime;

public class CliDtos {

    @Data
    public static class GenerateTokenRequest {
        private String name;
    }

    @Data
    public static class RenameTokenRequest {
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
}