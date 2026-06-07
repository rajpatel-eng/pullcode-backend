package com.capstoneproject.codereviewsystem.kafka.consumer;

import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import com.capstoneproject.codereviewsystem.kafka.KafkaTopics;
import com.capstoneproject.codereviewsystem.kafka.events.ReviewReadyEvent;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.ProjectCommitRepository;
import com.capstoneproject.codereviewsystem.services.encryption.EncryptionService;
import com.capstoneproject.codereviewsystem.services.review.*;
import com.capstoneproject.codereviewsystem.sse.ReviewProgressEvent;
import com.capstoneproject.codereviewsystem.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewConsumer {

    private final AiModelRepository         aiModelRepository;
    private final CommitHistoryRepository   commitHistoryRepo;
    private final ProjectCommitRepository   projectCommitRepo;
    private final EncryptionService         encryptionService;
    private final AiReviewService           aiReviewService;
    private final AiReviewPromptBuilder     promptBuilder;
    private final AiReviewResultParser      resultParser;
    private final FileReviewService         fileReviewService;
    private final TempReviewStager          tempStager;
    private final SseEmitterRegistry        sseRegistry;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;

    @KafkaListener(
            topics = KafkaTopics.REVIEW_READY,
            groupId = "ai-review-group",
            containerFactory = "readyKafkaListenerContainerFactory"
    )
    public void consume(ReviewReadyEvent event) {
        String key = "ai:" + event.getEventId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        log.info("AiReview started: eventId={} changedFiles={} unchangedFiles={}",
                event.getEventId(),
                event.getChangedFiles().size(),
                event.getUnchangedFiles().size());

        CommitHistory commitHistory = event.getCommitHistoryId() != null
                ? commitHistoryRepo.findById(event.getCommitHistoryId()).orElse(null)
                : null;

        ProjectCommit projectCommit = event.getProjectCommitId() != null
                ? projectCommitRepo.findById(event.getProjectCommitId()).orElse(null)
                : null;

        Map<String, String> hashSnapshot = event.getHashSnapshot() != null
                ? event.getHashSnapshot()
                : Map.of();

        if (event.getAiModelId() == null) {
            log.info("No AI model configured for this repo/project — skipping AI, saving rows only");

            sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                    .stage(ReviewProgressEvent.Stage.AI_MODEL_NOT_CONFIGURED)
                    .message("⚠️ No AI model configured for this project — go to project settings and assign one")
                    .source(event.getSource().name())
                    .build());

            fileReviewService.saveReviewResults(
                    commitHistory, projectCommit,
                    null,           // no model
                    Map.of(),       // no fresh errors
                    List.of(),      // no changed files sent to AI
                    mergeAllFiles(event),
                    hashSnapshot);

            tempStager.cleanup(event.getTempStagingPath());
            redisTemplate.opsForValue().set(key,"processed",Duration.ofDays(7));
            return;
        }

        AiModel model = aiModelRepository.findById(event.getAiModelId()).orElse(null);

        if (model == null || !model.isActive() || model.isDeleted()) {
            String reason = model == null ? "not found"
                    : model.isDeleted() ? "deleted" : "inactive";
            log.warn("AI model {} is {} — skipping AI", event.getAiModelId(), reason);

            sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                    .stage(ReviewProgressEvent.Stage.AI_MODEL_NOT_CONFIGURED)
                    .message("⚠️ AI model is " + reason + " — go to project settings and assign an active model")
                    .source(event.getSource().name())
                    .build());

            fileReviewService.saveReviewResults(
                    commitHistory, projectCommit,
                    null, Map.of(), List.of(), mergeAllFiles(event), hashSnapshot);

            tempStager.cleanup(event.getTempStagingPath());
            return;
        }

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.AI_REVIEWING)
                .message("🤖 Sending " + event.getChangedFiles().size()
                        + " files to " + model.getName() + " (" + model.getProvider() + ")...")
                .source(event.getSource().name())
                .metadata(Map.of("modelName", model.getName(), "provider", model.getProvider()))
                .build());

        String apiKey;
        try {
            apiKey = encryptionService.decrypt(model.getEncryptedApiKey());
        } catch (Exception e) {
            log.error("API key decryption failed for model {}: {}", model.getId(), e.getMessage());
            emitError(event, "Failed to decrypt API key — rotate it in project settings");
            tempStager.cleanup(event.getTempStagingPath());
            return;
        }
        List<String> stagedFiles = tempStager.listStagedFiles(event.getTempStagingPath());
        if (stagedFiles.isEmpty()) {
            emitError(event, "No staged files found — temp folder may have been cleaned up");
            return;
        }

        Map<String, String> fileContents = readFileContents(
                event.getTempStagingPath(), stagedFiles);

        String prompt = promptBuilder.build(fileContents, event.getChangedFiles());

        String aiResponse;
        long startMs = System.currentTimeMillis();
        try {
            aiResponse = aiReviewService.review(model, apiKey, prompt);
        } catch (Exception e) {
            log.error("AI call failed eventId={}: {}", event.getEventId(), e.getMessage());
            emitError(event, "AI review failed: " + e.getMessage());
            tempStager.cleanup(event.getTempStagingPath());
            return;
        }
        long latencyMs = System.currentTimeMillis() - startMs;

        Map<String, List<FileReviewService.ParsedError>> freshErrors;
        try {
            freshErrors = resultParser.parse(aiResponse, stagedFiles);
        } catch (Exception e) {
            log.error("Parse failed eventId={}: {}", event.getEventId(), e.getMessage());
            emitError(event, "Failed to parse AI response: " + e.getMessage());
            tempStager.cleanup(event.getTempStagingPath());
            return;
        }

        int freshCount = freshErrors.values().stream().mapToInt(List::size).sum();

        try {
            fileReviewService.saveReviewResults(
                    commitHistory,
                    projectCommit,
                    model,
                    freshErrors,
                    event.getChangedFiles(),
                    event.getUnchangedFiles(),
                    hashSnapshot);
        } catch (Exception e) {
            log.error("DB save failed eventId={}: {}", event.getEventId(), e.getMessage());
            emitError(event, "Failed to save review results: " + e.getMessage());
            tempStager.cleanup(event.getTempStagingPath());
            return;
        }

        tempStager.cleanup(event.getTempStagingPath());

        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.REVIEW_COMPLETE)
                .message("✅ Review complete — " + freshCount + " issue(s) found in "
                        + event.getChangedFiles().size() + " changed files")
                .source(event.getSource().name())
                .metadata(Map.of(
                        "freshIssues",    freshCount,
                        "changedFiles",   event.getChangedFiles().size(),
                        "unchangedFiles", event.getUnchangedFiles().size(),
                        "modelName",      model.getName(),
                        "latencyMs",      latencyMs
                ))
                .build());

        log.info("AiReview complete: eventId={} freshIssues={} latencyMs={}",
                event.getEventId(), freshCount, latencyMs);
    }

    private Map<String, String> readFileContents(String tempPath, List<String> files) {
        Map<String, String> contents = new LinkedHashMap<>();
        Path root = Paths.get(basePath, tempPath);
        for (String rel : files) {
            try {
                contents.put(rel, Files.readString(root.resolve(rel)));
            } catch (Exception e) {
                log.warn("Could not read staged file {}: {}", rel, e.getMessage());
            }
        }
        return contents;
    }

    private List<String> mergeAllFiles(ReviewReadyEvent event) {
        List<String> all = new ArrayList<>(event.getChangedFiles());
        all.addAll(event.getUnchangedFiles());
        return all;
    }

    private void emitError(ReviewReadyEvent event, String message) {
        sseRegistry.emit(event.getUserId(), ReviewProgressEvent.builder()
                .stage(ReviewProgressEvent.Stage.ERROR)
                .message("❌ " + message)
                .source(event.getSource().name())
                .build());
    }
}
