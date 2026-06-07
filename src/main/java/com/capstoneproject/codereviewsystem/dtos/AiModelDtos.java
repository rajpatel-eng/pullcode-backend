package com.capstoneproject.codereviewsystem.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

public class AiModelDtos {

    @Data
    public static class CreateRequest {

        @NotBlank(message = "Model name is required")
        @Size(max = 100)
        private String name;

        @NotBlank(message = "Provider is required")
        @Size(max = 100)
        private String provider;

        @NotBlank(message = "API key is required")
        private String apiKey;

        @NotBlank(message = "API base URL is required (e.g. https://api.openai.com)")
        @Size(max = 500)
        private String apiBaseUrl;

        private String systemPrompt;

        private Double temperature;

        private Integer maxTokens;

        private String description;

        private boolean defaultModel = false;
    }


    @Data
    public static class UpdateRequest {

        @NotBlank(message = "Model name is required")
        @Size(max = 100)
        private String name;

        @NotBlank(message = "Provider is required")
        @Size(max = 100)
        private String provider;

        @NotBlank(message = "API base URL is required (e.g. https://api.openai.com)")
        @Size(max = 500)
        private String apiBaseUrl;

        private String systemPrompt;

        private Double temperature;

        private Integer maxTokens;

        private String description;
    }

    @Data
    public static class ApiKeyUpdateRequest {

        @NotBlank(message = "New API key is required")
        private String newApiKey;
    }


    @Data
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String provider;
        private String description;
        private boolean active;
        private boolean defaultModel;

        private String apiKeyMask;
        private String apiBaseUrl;
        private String systemPrompt;
        private Double temperature;
        private Integer maxTokens;

        private String createdByEmail;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    public static class SummaryResponse {
        private Long id;
        private String name;
        private String provider;
        private boolean defaultModel;
    }
}