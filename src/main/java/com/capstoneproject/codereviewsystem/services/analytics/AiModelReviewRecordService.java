package com.capstoneproject.codereviewsystem.services.analytics;

import com.capstoneproject.codereviewsystem.entity.*;
import com.capstoneproject.codereviewsystem.entity.AiModelReviewRecord.ReviewOutcome;
import com.capstoneproject.codereviewsystem.repos.AiModelReviewRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Integration point between the existing AI review pipeline and the analytics system.
 *
 * HOW TO INTEGRATE:
 * -----------------
 * In your existing AiReviewService (or wherever reviews complete/fail),
 * inject this service and call the appropriate method:
 *
 *   // On successful review:
 *   reviewRecordService.recordSuccess(model, repo, commit,
 *       latencyMs, generationMs, inputTokens, outputTokens, cost);
 *
 *   // On failure:
 *   reviewRecordService.recordFailure(model, repo, commit,
 *       ReviewOutcome.FAILED, latencyMs, errorMessage);
 *
 *   // When user rates a review:
 *   reviewRecordService.recordUserFeedback(recordId, rating, helpful, accepted);
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelReviewRecordService {

    private final AiModelReviewRecordRepository reviewRecordRepo;

    // ── Record a successful review ────────────────────────────────────────────

    @Transactional
    public AiModelReviewRecord recordSuccess(AiModel model,
                                              CodeRepository repo,
                                              CommitHistory commit,
                                              long latencyMs,
                                              long reviewGenerationMs,
                                              long inputTokens,
                                              long outputTokens,
                                              BigDecimal cost) {
        AiModelReviewRecord record = AiModelReviewRecord.builder()
                .aiModel(model)
                .repository(repo)
                .commitHistory(commit)
                .outcome(ReviewOutcome.SUCCESS)
                .latencyMs(latencyMs)
                .reviewGenerationMs(reviewGenerationMs)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(inputTokens + outputTokens)
                .cost(cost)
                .build();

        reviewRecordRepo.save(record);
        log.debug("Analytics: recorded SUCCESS for model={} repo={} latency={}ms tokens={}",
                model.getName(), repo.getId(), latencyMs, inputTokens + outputTokens);
        return record;
    }

    // ── Record a failed / timed-out / rate-limited review ────────────────────

    @Transactional
    public AiModelReviewRecord recordFailure(AiModel model,
                                              CodeRepository repo,
                                              CommitHistory commit,
                                              ReviewOutcome outcome,
                                              Long latencyMs,
                                              String errorMessage) {
        AiModelReviewRecord record = AiModelReviewRecord.builder()
                .aiModel(model)
                .repository(repo)
                .commitHistory(commit)
                .outcome(outcome)
                .latencyMs(latencyMs)
                .inputTokens(0L)
                .outputTokens(0L)
                .totalTokens(0L)
                .cost(BigDecimal.ZERO)
                .errorMessage(errorMessage)
                .build();

        reviewRecordRepo.save(record);
        log.warn("Analytics: recorded {} for model={} repo={}: {}",
                outcome, model.getName(), repo.getId(), errorMessage);
        return record;
    }

    // ── Record user feedback on a completed review ────────────────────────────

    @Transactional
    public void recordUserFeedback(Long recordId,
                                    Integer rating,
                                    boolean markedHelpful,
                                    boolean suggestionAccepted) {
        reviewRecordRepo.findById(recordId).ifPresent(record -> {
            if (rating != null && rating >= 1 && rating <= 5) {
                record.setUserRating(rating);
            }
            record.setMarkedHelpful(markedHelpful);
            record.setSuggestionAccepted(suggestionAccepted);
            reviewRecordRepo.save(record);
            log.debug("Analytics: user feedback recorded for record={} rating={} helpful={}",
                    recordId, rating, markedHelpful);
        });
    }

    // ── Record false positive / negative flag ─────────────────────────────────

    @Transactional
    public void recordQualityFlag(Long recordId, boolean isFalsePositive, boolean isFalseNegative) {
        reviewRecordRepo.findById(recordId).ifPresent(record -> {
            record.setFalsePositive(isFalsePositive);
            record.setFalseNegative(isFalseNegative);
            reviewRecordRepo.save(record);
        });
    }

    // ── Record AI-assigned review score ──────────────────────────────────────

    @Transactional
    public void recordReviewScore(Long recordId, BigDecimal score) {
        reviewRecordRepo.findById(recordId).ifPresent(record -> {
            record.setReviewScore(score);
            reviewRecordRepo.save(record);
        });
    }
}
