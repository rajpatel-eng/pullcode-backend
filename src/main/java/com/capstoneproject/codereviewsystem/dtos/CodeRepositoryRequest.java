package com.capstoneproject.codereviewsystem.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CodeRepositoryRequest {

    @NotBlank(message = "Title is required")
    private String title;           

    @NotBlank(message = "Repository URL is required")
    @Pattern(
        regexp = "https://(github\\.com|gitlab\\.com|bitbucket\\.org)/.+/.+",
        message = "Must be a valid GitHub, GitLab, or Bitbucket URL"
    )
    private String repoUrl;             

    private String accessToken;    

    private String branch;
}