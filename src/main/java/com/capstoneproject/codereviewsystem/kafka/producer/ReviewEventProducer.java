package com.capstoneproject.codereviewsystem.kafka.producer;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewSubmittedEvent;
import com.capstoneproject.codereviewsystem.sse.ReviewProgressEvent;
import com.capstoneproject.codereviewsystem.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SseEmitterRegistry sseRegistry;

    public void submitWebhook(CommitHistory commitHistory, String storagePath) {
        AiModel aiModel = commitHistory.getRepository().getAiModel();

        ReviewSubmittedEvent event = ReviewSubmittedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .source(ReviewSubmittedEvent.Source.WEBHOOK)
                .userId(commitHistory.getUser().getId())
                .commitHistoryId(commitHistory.getId())
                .storagePath(storagePath)
                .aiModelId(aiModel != null ? aiModel.getId() : null)
                .submittedAt(LocalDateTime.now())
                .build();

        publish(event, commitHistory.getCommitId());
    }

    public void submitZip(ProjectCommit projectCommit,
                          String storagePath,
                          ReviewSubmittedEvent.Source source) {

        AiModel aiModel = projectCommit.getZipProject().getAiModel();

        ReviewSubmittedEvent event = ReviewSubmittedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .source(source)
                .userId(projectCommit.getUser().getId())
                .projectCommitId(projectCommit.getId())
                .storagePath(storagePath)
                .aiModelId(aiModel != null ? aiModel.getId() : null)
                .submittedAt(LocalDateTime.now())
                .build();

        publish(event, projectCommit.getCommitHash());
    }

    private void publish(ReviewSubmittedEvent event, String commitId) {
        String msg = switch (event.getSource()) {
            case WEBHOOK -> "📡 Push detected — commit " + shortId(commitId) + " queued for review";
            case ZIP_UI  -> "📤 ZIP upload received — queued for review";
            case CLI     -> "💻 CLI push received — queued for review";
        };

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.COMMIT_DETECTED)
                .message(msg)
                .commitId(commitId)
                .source(event.getSource().name())
                .build());

        kafkaTemplate.send(KafkaTopics.REVIEW_SUBMITTED,
                String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ReviewSubmittedEvent: {}", ex.getMessage());
                        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                                .stage(ReviewProgressEvent.Stage.ERROR)
                                .message("❌ Failed to queue review: " + ex.getMessage())
                                .commitId(commitId)
                                .source(event.getSource().name())
                                .build());
                    } else {
                        log.info("ReviewSubmittedEvent published: eventId={} source={} aiModelId={}",
                                event.getEventId(), event.getSource(), event.getAiModelId());
                    }
                });
    }

    private String shortId(String id) {
        return id != null && id.length() > 7 ? id.substring(0, 7) : id;
    }
}
