package com.capstoneproject.codereviewsystem.services.review;

import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos;
import com.capstoneproject.codereviewsystem.entity.*;
import com.capstoneproject.codereviewsystem.entity.FileReviewError.Severity;
import com.capstoneproject.codereviewsystem.repos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileReviewService {

    private final FileReviewRepository      fileReviewRepo;
    private final FileReviewErrorRepository errorRepo;
    private final CommitHistoryRepository   commitHistoryRepo;
    private final ProjectCommitRepository   projectCommitRepo;

    @Transactional
    public void saveReviewResults(
            CommitHistory commitHistory,
            ProjectCommit projectCommit,
            AiModel aiModel,
            Map<String, List<ParsedError>> freshErrors,
            List<String> changedFiles,
            List<String> unchangedFiles,
            Map<String, String> hashSnapshot) {

        Long repoId    = commitHistory != null ? commitHistory.getRepository().getId() : null;
        Long projectId = projectCommit  != null ? projectCommit.getZipProject().getId() : null;

        int savedFresh   = 0;
        int savedCarried = 0;
        int savedNever   = 0;

        for (String filePath : changedFiles) {
            List<ParsedError> errors = freshErrors.getOrDefault(filePath, List.of());

            FileReview fr = FileReview.builder()
                    .commitHistory(commitHistory)
                    .projectCommit(projectCommit)
                    .filePath(filePath)
                    .fileHash(hashSnapshot.getOrDefault(filePath, null))
                    .sentToAi(true)
                    .neverReviewed(false)
                    .aiModel(aiModel)
                    .build();
            fileReviewRepo.save(fr);

            for (ParsedError err : errors) {
                errorRepo.save(FileReviewError.builder()
                        .fileReview(fr)
                        .commitHistory(commitHistory)
                        .projectCommit(projectCommit)
                        .line(err.line())
                        .column(err.column())
                        .severity(mapSeverity(err.severity()))
                        .message(err.message())
                        .ruleId(err.ruleId())
                        .suggestion(err.suggestion())
                        .fresh(true)
                        .build());
            }
            savedFresh++;
        }

        for (String filePath : unchangedFiles) {

            Optional<FileReview> prevReview = repoId != null
                    ? fileReviewRepo.findLatestReviewedByRepoAndFilePath(repoId, filePath)
                    : fileReviewRepo.findLatestReviewedByProjectAndFilePath(projectId, filePath);

            if (prevReview.isEmpty()) {
                fileReviewRepo.save(FileReview.builder()
                        .commitHistory(commitHistory)
                        .projectCommit(projectCommit)
                        .filePath(filePath)
                        .fileHash(hashSnapshot.getOrDefault(filePath, null))
                        .sentToAi(false)
                        .neverReviewed(true)
                        .build());
                savedNever++;

            } else {
                fileReviewRepo.save(FileReview.builder()
                        .commitHistory(commitHistory)
                        .projectCommit(projectCommit)
                        .filePath(filePath)
                        .fileHash(hashSnapshot.getOrDefault(filePath, null))
                        .sentToAi(false)
                        .neverReviewed(false)
                        .sourceFileReview(prevReview.get())
                        .build());
                savedCarried++;
            }
        }

        if (commitHistory != null) {
            commitHistory.setReviewStatus(CommitHistory.ReviewStatus.COMPLETED);
            commitHistoryRepo.save(commitHistory);
        }
        if (projectCommit != null) {
            projectCommit.setReviewStatus(ProjectCommit.ReviewStatus.COMPLETED);
            projectCommitRepo.save(projectCommit);
        }

        log.info("FileReview saved: fresh={} carriedForward={} neverReviewed={}",
                savedFresh, savedCarried, savedNever);
    }


    @Transactional
    public void saveReviewResults(
            CommitHistory commitHistory,
            ProjectCommit projectCommit,
            AiModel aiModel,
            Map<String, List<ParsedError>> freshErrors,
            List<String> changedFiles,
            List<String> unchangedFiles) {
        saveReviewResults(commitHistory, projectCommit, aiModel,
                freshErrors, changedFiles, unchangedFiles, Map.of());
    }


    @Transactional(readOnly = true)
    public FileReviewDtos.CommitReviewResponse getCommitHistoryReview(Long commitHistoryId,Long currentUserId) {
        CommitHistory ch = commitHistoryRepo.findById(commitHistoryId)
                .orElseThrow(() -> new RuntimeException("Commit not found: " + commitHistoryId));

        if (ch.getUser() == null || !ch.getUser().getId().equals(currentUserId)) {
                throw new AccessDeniedException("You are not allowed to access this review");
        }
        List<FileReview> fileReviews =
                fileReviewRepo.findByCommitHistoryOrderByFilePath(ch);

        List<Object[]> severityCounts =
                errorRepo.countBySeverityForCommitHistory(commitHistoryId);

        return buildResponse(
                ch.getCommitId(), "WEBHOOK",
                ch.getReceivedAt(), fileReviews,
                severityCounts);
    }


    @Transactional(readOnly = true)
    public FileReviewDtos.CommitReviewResponse getProjectCommitReview(Long projectCommitId,Long currentUserId) {
        ProjectCommit pc = projectCommitRepo.findById(projectCommitId)
                .orElseThrow(() -> new RuntimeException("ProjectCommit not found: " + projectCommitId));

        if (pc.getUser() == null || !pc.getUser().getId().equals(currentUserId)) {
                throw new AccessDeniedException("You are not allowed to access this review");
        }
        List<FileReview> fileReviews =
                fileReviewRepo.findByProjectCommitOrderByFilePath(pc);

        List<Object[]> severityCounts =
                errorRepo.countBySeverityForProjectCommit(projectCommitId);

        return buildResponse(
                pc.getCommitHash(), pc.getSource().name(),
                pc.getCommittedAt(), fileReviews,
                severityCounts);
    }


    private FileReviewDtos.CommitReviewResponse buildResponse(
            String commitId,
            String source,
            java.time.LocalDateTime reviewedAt,
            List<FileReview> fileReviews,
            List<Object[]> severityCounts) {

        List<FileReviewDtos.FileReviewItem> items = new ArrayList<>();
        int totalErrors   = 0;
        int filesSentToAi = 0;
        int filesCarried  = 0;
        int filesNever    = 0;

        for (FileReview fr : fileReviews) {
            List<FileReviewError> errors = resolveErrors(fr);
            totalErrors += errors.size();

            if (fr.isSentToAi())           filesSentToAi++;
            else if (fr.isNeverReviewed()) filesNever++;
            else                           filesCarried++;

            String sourceCommitId = null;
            if (!fr.isSentToAi() && !fr.isNeverReviewed()
                    && fr.getSourceFileReview() != null) {
                FileReview src = fr.getSourceFileReview();
                if (src.getCommitHistory() != null)
                    sourceCommitId = src.getCommitHistory().getCommitId();
                else if (src.getProjectCommit() != null)
                    sourceCommitId = src.getProjectCommit().getCommitHash();
            }

            Map<String, Long> fileSeverityMap = errors.stream()
                    .collect(Collectors.groupingBy(
                            e -> e.getSeverity().name(), Collectors.counting()));

            boolean carriedForward = !fr.isSentToAi() && !fr.isNeverReviewed();
            List<FileReviewDtos.ErrorItem> errorItems = errors.stream()
                    .map(e -> toErrorItem(e, carriedForward ? false : e.isFresh()))
                    .toList();

            items.add(FileReviewDtos.FileReviewItem.builder()
                    .filePath(fr.getFilePath())
                    .sentToAi(fr.isSentToAi())
                    .neverReviewed(fr.isNeverReviewed())
                    .sourceCommitId(sourceCommitId)
                    .errorCount(errors.size())
                    .errorsBySeverity(fileSeverityMap)
                    .errors(errorItems)
                    .build());
        }

        Map<String, Long> totalSeverityMap = new LinkedHashMap<>();
        for (Object[] row : severityCounts) {
            totalSeverityMap.put(row[0].toString(), (Long) row[1]);
        }

        return FileReviewDtos.CommitReviewResponse.builder()
                .commitId(commitId)
                .source(source)
                .reviewedAt(reviewedAt)
                .totalFiles(fileReviews.size())
                .filesSentToAi(filesSentToAi)
                .filesCarriedForward(filesCarried)
                .filesNeverReviewed(filesNever)
                .totalErrors(totalErrors)
                .errorsBySeverity(totalSeverityMap)
                .files(items)
                .build();
    }

    private List<FileReviewError> resolveErrors(FileReview fr) {
        if (fr.isSentToAi()) {
            return errorRepo.findByFileReview(fr);
        }
        if (!fr.isNeverReviewed() && fr.getSourceFileReview() != null) {
            return errorRepo.findByFileReview(fr.getSourceFileReview());
        }
        return List.of();
    }

    private FileReviewDtos.ErrorItem toErrorItem(FileReviewError e, boolean fresh) {
        return FileReviewDtos.ErrorItem.builder()
                .id(e.getId())
                .line(e.getLine())
                .column(e.getColumn())
                .severity(e.getSeverity())
                .message(e.getMessage())
                .ruleId(e.getRuleId())
                .suggestion(e.getSuggestion())
                .fresh(fresh)
                .build();
    }

    private Severity mapSeverity(String raw) {
        if (raw == null) return Severity.MEDIUM;
        try {
            return Severity.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Severity.MEDIUM;
        }
    }


    public record ParsedError(
            String filePath,
            int line,
            int column,
            String severity,
            String message,
            String ruleId,
            String suggestion
    ) {}
}
