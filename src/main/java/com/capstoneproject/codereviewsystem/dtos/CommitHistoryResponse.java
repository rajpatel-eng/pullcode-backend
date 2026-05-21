package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.entity.CommitHistory.ReviewStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CommitHistoryResponse {
    private Long id;
    private String commitId;
    private String commitMessage;
    private String commitUrl;
    private String authorName;
    private String branch;
    private String filesChanged;
    private Integer filesAddedCount;
    private Integer filesModifiedCount;
    private Integer filesRemovedCount;
    private ReviewStatus reviewStatus;
    private LocalDateTime committedAt;
    private LocalDateTime receivedAt;

    private Long repositoryId;
    private String repositoryTitle;
    private String repoUrl;
}