package com.capstoneproject.codereviewsystem.services.project;

import com.capstoneproject.codereviewsystem.dtos.ExtractResult;
import com.capstoneproject.codereviewsystem.dtos.ProjectCommitDtos.*;
import com.capstoneproject.codereviewsystem.entity.CliToken;
import com.capstoneproject.codereviewsystem.entity.ProjectCommit;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.CliTokenRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.ProjectCommitRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.repos.ZipProjectRepository;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService.TokenValidationResult;
import com.capstoneproject.codereviewsystem.services.zip.ZipStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCommitService {

    private final ProjectCommitRepository commitRepository;
    private final ZipProjectRepository zipProjectRepository;
    private final CliTokenRepository cliTokenRepository;
    private final UserRepository userRepository;
    private final ZipStorageService zipStorageService;
    private final CliTokenService cliTokenService;
    private final CommitHistoryRepository commitHistoryRepository;


    @Transactional
    public CommitResponse uploadFromUi(Long projectId,
            MultipartFile zipFile,
            ZipUploadRequest req,
            Long userId) {

        if (req.getCommitMessage() == null || req.getCommitMessage().isBlank()) {
            throw new BadRequestException("Commit message is required");
        }
        if (req.getTokenId() == null) {
            throw new BadRequestException("A project token must be selected for the upload");
        }

        User user = getUser(userId);
        ZipProject project = getProject(projectId, user);

        CliToken token = cliTokenRepository
                .findByIdAndZipProjectAndUser(req.getTokenId(), project, user)
                .orElseThrow(() -> new BadRequestException(
                        "Selected token not found or does not belong to this project"));

        if (!token.isActive()) {
            throw new BadRequestException("Selected token is paused. Please choose an active token.");
        }

        ExtractResult extracted = extract(zipFile, userId, projectId);
        updateLatest(userId, projectId, extracted.getStoragePath());

        ProjectCommit commit = buildCommit(extracted, req.getCommitMessage(), req.getExtraMessage(),
                ProjectCommit.Source.ZIP_UI, project, user, token);
        commitRepository.save(commit);
        updateProjectMeta(project, extracted.getStoragePath());

        log.info("ZIP UI upload: project={} commit={} token='{}' files={} user={}",
                projectId, commit.getCommitHash(), token.getName(), extracted.getTotalFiles(), userId);

        return toCommitResponse(commit, project,
                "Upload successful. " + extracted.getTotalFiles() + " files stored, "
                        + extracted.getSkippedFiles() + " skipped.");
    }

    // ── CLI push ───────────────────────────────────────────────────────────

    @Transactional
    public CommitResponse pushFromCli(String rawToken,
            MultipartFile zipFile,
            String commitMessage) {

        if (commitMessage == null || commitMessage.isBlank()) {
            throw new BadRequestException("Commit message is required");
        }

        TokenValidationResult validated = cliTokenService.validateAndTouch(rawToken);
        CliToken token     = validated.token();
        User user          = validated.user();
        ZipProject project = validated.project();

        ExtractResult extracted = extract(zipFile, user.getId(), project.getId());
        updateLatest(user.getId(), project.getId(), extracted.getStoragePath());

        ProjectCommit commit = buildCommit(extracted, commitMessage, null,
                ProjectCommit.Source.CLI, project, user, token);
        commitRepository.save(commit);
        updateProjectMeta(project, extracted.getStoragePath());

        log.info("CLI push: project={} commit={} token='{}' files={} user={}",
                project.getId(), commit.getCommitHash(), token.getName(),
                extracted.getTotalFiles(), user.getId());

        return toCommitResponse(commit, project,
                "✓ Pushed to " + project.getTitle()
                        + " [" + commit.getCommitHash() + "] · " + token.getName());
    }

    // ── History ────────────────────────────────────────────────────────────

    public Page<CommitHistoryItem> getHistory(Long projectId, Long userId, int page, int size) {
        User user = getUser(userId);
        getProject(projectId, user); // ownership check

        return commitRepository
                .findByZipProjectIdAndUserIdOrderByCommittedAtDesc(
                        projectId, userId, PageRequest.of(page, size))
                .map(this::toHistoryItem);
    }

    public Page<UserCommitItem> getUserAllCommits(Long userId, int page, int size) {
        User user = getUser(userId);

        List<UserCommitItem> zipCli = commitRepository
                .findByUserIdOrderByCommittedAtDesc(userId, Pageable.unpaged())
                .stream()
                .map(c -> UserCommitItem.builder()
                        .id(c.getId())
                        .source(c.getSource().name())
                        .commitHash(c.getCommitHash())
                        .commitMessage(c.getCommitMessage())
                        .reviewStatus(c.getReviewStatus().name())
                        .committedAt(c.getCommittedAt())
                        .projectId(c.getZipProject().getId())
                        .projectTitle(c.getZipProject().getTitle())
                        .tokenName(c.getCliToken().getName())
                        .originalFileName(c.getOriginalFileName())
                        .totalFilesExtracted(c.getTotalFilesExtracted())
                        .build())
                .collect(Collectors.toList());

        List<UserCommitItem> webhook = commitHistoryRepository
                .findByUserOrderByCommittedAtDesc(user, Pageable.unpaged())
                .stream()
                .map(c -> UserCommitItem.builder()
                        .id(c.getId())
                        .source("WEBHOOK")
                        .commitHash(c.getCommitId())
                        .commitMessage(c.getCommitMessage())
                        .reviewStatus(c.getReviewStatus().name())
                        .committedAt(c.getCommittedAt())
                        .repositoryId(c.getRepository().getId())
                        .repositoryTitle(c.getRepository().getTitle())
                        .repoUrl(c.getRepository().getRepoUrl())
                        .branch(c.getBranch())
                        .authorName(c.getAuthorName())
                        .filesAddedCount(c.getFilesAddedCount())
                        .filesModifiedCount(c.getFilesModifiedCount())
                        .filesRemovedCount(c.getFilesRemovedCount())
                        .build())
                .collect(Collectors.toList());

        List<UserCommitItem> merged = new ArrayList<>();
        merged.addAll(zipCli);
        merged.addAll(webhook);
        merged.sort(Comparator.comparing(
                UserCommitItem::getCommittedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        int total   = merged.size();
        int fromIdx = Math.min(page * size, total);
        int toIdx   = Math.min(fromIdx + size, total);

        return new PageImpl<>(merged.subList(fromIdx, toIdx), PageRequest.of(page, size), total);
    }


    private ExtractResult extract(MultipartFile zipFile, Long userId, Long projectId) {
        try {
            return zipStorageService.extractAndStore(zipFile, userId, projectId);
        } catch (IOException e) {
            throw new BadRequestException("Failed to process ZIP: " + e.getMessage());
        }
    }

    private void updateLatest(Long userId, Long projectId, String storagePath) {
        try {
            zipStorageService.updateLatestFolder(userId, projectId, storagePath);
        } catch (IOException e) {
            log.warn("Could not update latest folder for project {}: {}", projectId, e.getMessage());
        }
    }

    private void updateProjectMeta(ZipProject project, String storagePath) {
        project.setLatestStoragePath(storagePath);
        project.setCommitCount(project.getCommitCount() + 1);
        zipProjectRepository.save(project);
    }

    private ProjectCommit buildCommit(ExtractResult extracted,
            String commitMessage,
            String extraMessage,
            ProjectCommit.Source source,
            ZipProject project,
            User user,
            CliToken token) {
        return ProjectCommit.builder()
                .commitHash(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .commitMessage(commitMessage)
                .extraMessage(extraMessage)
                .originalFileName(extracted.getOriginalFileName())
                .fileSizeBytes(extracted.getFileSizeBytes())
                .totalFilesExtracted(extracted.getTotalFiles())
                .storagePath(extracted.getStoragePath())
                .source(source)
                .reviewStatus(ProjectCommit.ReviewStatus.PENDING)
                .zipProject(project)
                .user(user)
                .cliToken(token)
                .build();
    }

    private CommitResponse toCommitResponse(ProjectCommit c, ZipProject project, String message) {
        return CommitResponse.builder()
                .id(c.getId())
                .commitHash(c.getCommitHash())
                .commitMessage(c.getCommitMessage())
                .extraMessage(c.getExtraMessage())
                .originalFileName(c.getOriginalFileName())
                .totalFilesExtracted(c.getTotalFilesExtracted())
                .fileSizeBytes(c.getFileSizeBytes())
                .storagePath(c.getStoragePath())
                .source(c.getSource().name())
                .reviewStatus(c.getReviewStatus().name())
                .projectId(project.getId())
                .projectTitle(project.getTitle())
                .tokenName(c.getCliToken().getName())
                .committedAt(c.getCommittedAt())
                .message(message)
                .build();
    }

    private CommitHistoryItem toHistoryItem(ProjectCommit c) {
        return CommitHistoryItem.builder()
                .id(c.getId())
                .commitHash(c.getCommitHash())
                .commitMessage(c.getCommitMessage())
                .extraMessage(c.getExtraMessage())
                .originalFileName(c.getOriginalFileName())
                .fileSizeBytes(c.getFileSizeBytes())
                .totalFilesExtracted(c.getTotalFilesExtracted())
                .source(c.getSource().name())
                .tokenName(c.getCliToken().getName())
                .reviewStatus(c.getReviewStatus().name())
                .committedAt(c.getCommittedAt())
                .build();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    private ZipProject getProject(Long projectId, User user) {
        return zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));
    }
}