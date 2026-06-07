package com.capstoneproject.codereviewsystem.kafka.events;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReadyEvent {

    private String eventId;
    private ReviewSubmittedEvent.Source source;
    private Long userId;

    private Long commitHistoryId;
    private Long projectCommitId;

    private String tempStagingPath;

    private List<String> changedFiles;

    private List<String> unchangedFiles;

    private Map<String, String> hashSnapshot;

    private Long aiModelId;

    private LocalDateTime submittedAt;
    private LocalDateTime stagedAt;
}
