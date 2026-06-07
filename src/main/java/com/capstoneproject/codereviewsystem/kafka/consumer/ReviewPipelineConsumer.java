package com.capstoneproject.codereviewsystem.kafka.consumer;

import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewReadyEvent;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewSubmittedEvent;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.ProjectCommitRepository;
import com.capstoneproject.codereviewsystem.services.review.FileHashService;
import com.capstoneproject.codereviewsystem.services.review.ImportResolverService;
import com.capstoneproject.codereviewsystem.services.review.TempReviewStager;
import com.capstoneproject.codereviewsystem.sse.ReviewProgressEvent;
import com.capstoneproject.codereviewsystem.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPipelineConsumer {

    private final FileHashService           fileHashService;
    private final ImportResolverService     importResolver;
    private final TempReviewStager          tempStager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SseEmitterRegistry        sseRegistry;
    private final CommitHistoryRepository   commitHistoryRepo;
    private final ProjectCommitRepository   projectCommitRepo;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(
            topics = KafkaTopics.REVIEW_CLEAN,
            groupId = "pipeline-group",
            containerFactory = "cleanKafkaListenerContainerFactory"
    )
    public void consume(ReviewSubmittedEvent event) {
        log.info("Pipeline started: eventId={}", event.getEventId());
        String key = "pipeline:" + event.getEventId();

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
        return;
        }
        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.HASH_DIFFING)
                .message("🔄 Comparing file hashes with last review...")
                .source(event.getSource().name())
                .build());

        Long repoId     = resolveRepoId(event);
        Long projectId  = resolveProjectId(event);

        FileHashService.DiffResult diff;
        try {
            diff = fileHashService.diff(event.getStoragePath(), repoId, projectId);
        } catch (Exception e) {
            log.error("Hash diff failed: {}", e.getMessage());
            emitError(event, "Hash comparison failed: " + e.getMessage());
            return;
        }

        if (!diff.hasChanges()) {
            sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                    .stage(ReviewProgressEvent.Stage.NO_CHANGES)
                    .message("✅ No file changes detected since last review")
                    .source(event.getSource().name())
                    .metadata(Map.of(
                            "totalFiles",     diff.totalFiles(),
                            "unchangedFiles", diff.unchangedFiles().size()
                    ))
                    .build());
            return;
        }

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.DIFF_RESULT)
                .message("📝 " + diff.changedFiles().size() + " changed, "
                        + diff.unchangedFiles().size() + " unchanged")
                .source(event.getSource().name())
                .metadata(Map.of(
                        "changedFiles",   diff.changedFiles().size(),
                        "unchangedFiles", diff.unchangedFiles().size(),
                        "newFiles",       diff.newFiles().size(),
                        "modifiedFiles",  diff.modifiedFiles().size()
                ))
                .build());

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.IMPORT_RESOLVING)
                .message("🔗 Resolving imports for " + diff.changedFiles().size() + " changed files...")
                .source(event.getSource().name())
                .build());

        Set<String> importedFiles;
        try {
            importedFiles = importResolver.resolveImports(
                    event.getStoragePath(), diff.changedFiles());
        } catch (Exception e) {
            log.warn("Import resolution failed — continuing without imports: {}", e.getMessage());
            importedFiles = Set.of();
        }

        String commitId = event.getStoragePath()
                .substring(event.getStoragePath().lastIndexOf('/') + 1);

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.STAGING)
                .message("📦 Staging " + (diff.changedFiles().size() + importedFiles.size())
                        + " files to temp/...")
                .source(event.getSource().name())
                .build());

        String tempPath;
        try {
            tempPath = tempStager.stage(
                    event.getStoragePath(),
                    commitId,
                    diff.changedFiles(),
                    importedFiles);
        } catch (Exception e) {
            log.error("Staging failed: {}", e.getMessage());
            emitError(event, "Failed to stage files: " + e.getMessage());
            return;
        }

        ReviewReadyEvent readyEvent = ReviewReadyEvent.builder()
                .eventId(event.getEventId())
                .source(event.getSource())
                .userId(event.getUserId())
                .commitHistoryId(event.getCommitHistoryId())
                .projectCommitId(event.getProjectCommitId())
                .tempStagingPath(tempPath)
                .changedFiles(diff.changedFiles())
                .unchangedFiles(diff.unchangedFiles())
                .hashSnapshot(diff.hashSnapshot())
                .aiModelId(event.getAiModelId())
                .submittedAt(event.getSubmittedAt())
                .stagedAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(KafkaTopics.REVIEW_READY,
                String.valueOf(event.getUserId()), readyEvent);

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.REVIEW_READY)
                .message("🚀 " + diff.changedFiles().size() + " files staged — sending to AI...")
                .source(event.getSource().name())
                .metadata(Map.of(
                        "tempPath",       tempPath,
                        "changedFiles",   diff.changedFiles().size(),
                        "importFiles",    importedFiles.size(),
                        "unchangedFiles", diff.unchangedFiles().size()
                ))
                .build());
        redisTemplate.opsForValue().set(key,"processed",Duration.ofDays(7));
        log.info("ReviewReadyEvent published: eventId={} changed={} unchanged={}",
                event.getEventId(), diff.changedFiles().size(), diff.unchangedFiles().size());
    }

    private Long resolveRepoId(ReviewSubmittedEvent event) {
        if (event.getCommitHistoryId() == null) return null;
        return commitHistoryRepo.findById(event.getCommitHistoryId())
                .map(ch -> ch.getRepository().getId())
                .orElse(null);
    }

    private Long resolveProjectId(ReviewSubmittedEvent event) {
        if (event.getProjectCommitId() == null) return null;
        return projectCommitRepo.findById(event.getProjectCommitId())
                .map(pc -> pc.getZipProject().getId())
                .orElse(null);
    }

    private void emitError(ReviewSubmittedEvent event, String message) {
        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.ERROR)
                .message("❌ " + message)
                .source(event.getSource().name())
                .build());
    }
}
