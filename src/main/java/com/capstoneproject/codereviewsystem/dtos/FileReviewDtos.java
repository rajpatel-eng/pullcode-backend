package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.entity.FileReviewError.Severity;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class FileReviewDtos {


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommitReviewResponse {

        private String commitId;
        private String source;              
        private LocalDateTime reviewedAt;

        private int totalFiles;
        private int filesSentToAi;          
        private int filesCarriedForward;    
        private int filesNeverReviewed;     
        private int totalErrors;
        private Map<String, Long> errorsBySeverity; 

        private List<FileReviewItem> files;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileReviewItem {

        private String filePath;

        private boolean sentToAi;

        private boolean neverReviewed;

        private String sourceCommitId;

        private int errorCount;
        private Map<String, Long> errorsBySeverity;
        private List<ErrorItem> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorItem {
        private Long id;
        private int line;
        private int column;
        private Severity severity;
        private String message;
        private String ruleId;
        private String suggestion;
        private boolean fresh;
    }
}
