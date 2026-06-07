package com.capstoneproject.codereviewsystem.kafka.events;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSubmittedEvent {

    public enum Source { WEBHOOK, ZIP_UI, CLI }

    private String eventId;     
    private Source source;

    private Long userId;

    private Long commitHistoryId;

    private Long projectCommitId;

    private String storagePath;

    private Long aiModelId;

    private LocalDateTime submittedAt;
}
