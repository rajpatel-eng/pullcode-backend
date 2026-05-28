package com.capstoneproject.codereviewsystem.services.cli;

import com.capstoneproject.codereviewsystem.dtos.CliDtos.*;
import com.capstoneproject.codereviewsystem.dtos.ExtractResult;
import com.capstoneproject.codereviewsystem.entity.CliCommitHistory;
import com.capstoneproject.codereviewsystem.entity.CliToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.CliCommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.ZipProjectRepository;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService.TokenValidationResult;
import com.capstoneproject.codereviewsystem.services.zip.ZipStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CliPushService {

        private final CliTokenService cliTokenService;
        private final CliCommitHistoryRepository commitHistoryRepository;
        private final ZipProjectRepository zipProjectRepository;
        private final ZipStorageService zipStorageService;

        @Transactional
        public CliPushResponse push(
                        String rawToken,
                        MultipartFile zipFile,
                        String commitMessage,
                        String pusherHostname,
                        String pusherOsUser) {

                if (commitMessage == null || commitMessage.isBlank()) {
                        throw new BadRequestException("Commit message is required");
                }

                TokenValidationResult validated = cliTokenService.validateAndTouch(rawToken);
                CliToken token = validated.token();
                User user = validated.user();
                ZipProject project = validated.project();

                ExtractResult extracted;
                try {
                        extracted = zipStorageService.extractAndStore(zipFile, user.getId(), project.getId());
                } catch (IOException e) {
                        log.error("ZIP extraction failed: {}", e.getMessage());
                        throw new BadRequestException("Failed to process ZIP file: " + e.getMessage());
                }

                try {
                        zipStorageService.updateLatestFolder(user.getId(), project.getId(), extracted.getStoragePath());
                } catch (IOException e) {
                        log.warn("Could not update latest folder: {}", e.getMessage());
                }

                String commitHash = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

                CliCommitHistory commit = CliCommitHistory.builder()
                                .commitHash(commitHash)
                                .commitMessage(commitMessage)
                                .originalFileName(extracted.getOriginalFileName())
                                .fileSizeBytes(extracted.getFileSizeBytes())
                                .totalFilesExtracted(extracted.getTotalFiles())
                                .storagePath(extracted.getStoragePath())
                                .pusherHostname(pusherHostname)
                                .pusherOsUser(pusherOsUser)
                                .zipProject(project)
                                .user(user)
                                .cliToken(token)
                                .reviewStatus(CliCommitHistory.ReviewStatus.PENDING)
                                .build();

                commitHistoryRepository.save(commit);

                project.setUploadCount(project.getUploadCount() + 1);
                project.setLatestStoragePath(extracted.getStoragePath());
                zipProjectRepository.save(project);

                long totalCommits = commitHistoryRepository.countByZipProject(project);

                String committedBy = token.getName();

                log.info("CLI push success: project={} commit={} token='{}' files={}",
                                project.getId(), commitHash, token.getName(), extracted.getTotalFiles());

                return CliPushResponse.builder()
                                .commitHash(commitHash)
                                .commitMessage(commitMessage)
                                .projectTitle(project.getTitle())
                                .projectId(project.getId())
                                .originalFileName(extracted.getOriginalFileName())
                                .totalFilesExtracted(extracted.getTotalFiles())
                                .fileSizeBytes(extracted.getFileSizeBytes())
                                .storagePath(extracted.getStoragePath())
                                .reviewStatus("PENDING")
                                .totalCommits(totalCommits)
                                .pushedAt(commit.getPushedAt())
                                .committedBy(committedBy)
                                .message("✓ Pushed to " + project.getTitle() + " [" + commitHash + "] · " + committedBy)
                                .build();
        }

        public ProjectCliHistoryResponse getCliHistory(Long projectId, Long userId) {
                ZipProject project = zipProjectRepository.findById(projectId)
                                .filter(p -> p.getUser().getId().equals(userId))
                                .orElseThrow(() -> new BadRequestException("Project not found"));

                List<CliCommitHistory> commits = commitHistoryRepository.findByZipProjectOrderByPushedAtDesc(project);

                List<CommitHistoryItem> items = commits.stream()
                                .map(c -> CommitHistoryItem.builder()
                                                .id(c.getId())
                                                .commitHash(c.getCommitHash())
                                                .commitMessage(c.getCommitMessage())
                                                .originalFileName(c.getOriginalFileName())
                                                .fileSizeBytes(c.getFileSizeBytes())
                                                .totalFilesExtracted(c.getTotalFilesExtracted())
                                                .tokenName(c.getCliToken() != null ? c.getCliToken().getName()
                                                                : "Unknown Token")
                                                .pusherHostname(c.getPusherHostname())
                                                .pusherOsUser(c.getPusherOsUser())
                                                .reviewStatus(c.getReviewStatus().name())
                                                .pushedAt(c.getPushedAt())
                                                .build())
                                .collect(Collectors.toList());

                return ProjectCliHistoryResponse.builder()
                                .projectId(project.getId())
                                .projectTitle(project.getTitle())
                                .totalCommits(commits.size())
                                .commits(items)
                                .build();
        }
}