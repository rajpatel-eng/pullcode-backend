package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.entity.CodeRepository.RepoProvider;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CodeRepositoryResponse {
    private Long id;
    private String title;
    private String repoUrl;
    private RepoProvider provider;
    private boolean hasAccessToken;
    private String webhookStatus;
    private LocalDateTime createdAt;
    private String webhookSecret;
    private String webhookUrl;
    private Long aiModelId;
    private String aiModelName;
    private String aiModelProvider;
}