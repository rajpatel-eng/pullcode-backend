package com.capstoneproject.codereviewsystem.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProfileResponse {
    private Long id;
    private String name;
    private String email;
    private String avatarUrl;
    private String authProvider;

    private long githubReviews;
    private long zipReviews;
    private long cliReviews;
    private long totalReviews;
}
