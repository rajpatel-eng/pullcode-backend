package com.capstoneproject.codereviewsystem.sse;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewProgressEvent {

    public enum Stage {
        COMMIT_DETECTED,
        FILES_FETCHING,
        FILES_STORED,

        SECURITY_SCANNING,
        SECURITY_THREAT,
        SECURITY_PASSED,

        HASH_DIFFING,
        DIFF_RESULT,
        NO_CHANGES,

        IMPORT_RESOLVING,
        STAGING,

        AI_MODEL_NOT_CONFIGURED,
        AI_REVIEWING,
        REVIEW_READY,
        REVIEW_COMPLETE,

        ERROR
    }

    private Stage stage;
    private String message;
    private String commitId;
    private String source;
    private Object metadata;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
