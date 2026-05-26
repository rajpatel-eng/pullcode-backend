package com.capstoneproject.codereviewsystem.services.zip;

import com.capstoneproject.codereviewsystem.dtos.ZipProjectDtos.*;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.entity.ZipUploadHistory;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.repos.ZipProjectRepository;
import com.capstoneproject.codereviewsystem.repos.ZipUploadHistoryRepository;
import com.capstoneproject.codereviewsystem.dtos.ExtractResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZipProjectService {

    private final ZipProjectRepository zipProjectRepository;
    private final ZipUploadHistoryRepository zipUploadHistoryRepository;
    private final ZipStorageService zipStorageService;
    private final UserRepository userRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, Long userId) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BadRequestException("Project title is required");
        }

        User user = getUser(userId);

        ZipProject project = ZipProject.builder()
                .title(req.getTitle().trim())
                .description(req.getDescription())
                .user(user)
                .uploadCount(0)
                .build();

        ZipProject saved = zipProjectRepository.save(project);
        log.info("ZIP project created: '{}' by user: {}", saved.getTitle(), userId);
        return toProjectResponse(saved);
    }

    public List<ProjectResponse> getAllProjects(Long userId) {
        User user = getUser(userId);
        return zipProjectRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toProjectResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProjectWithHistoryResponse getProjectWithHistory(Long projectId, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        List<UploadHistoryResponse> history = zipUploadHistoryRepository
                .findByZipProjectOrderByUploadedAtDesc(project)
                .stream()
                .map(h -> toHistoryResponse(h, project))
                .collect(Collectors.toList());

        return ProjectWithHistoryResponse.builder()
                .project(toProjectResponse(project))
                .uploadHistory(history)
                .build();
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, UpdateProjectRequest req, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            project.setTitle(req.getTitle().trim());
        }
        if (req.getDescription() != null) {
            project.setDescription(req.getDescription());
        }

        return toProjectResponse(zipProjectRepository.save(project));
    }

    @Transactional
    public void deleteProject(Long projectId, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        zipStorageService.deleteProjectFolder(userId, projectId);
        zipProjectRepository.delete(project);
        log.info("ZIP project deleted: {} by user: {}", projectId, userId);
    }

    @Transactional
    public UploadResponse uploadZip(Long projectId, MultipartFile file,
                                    String subtitle, String commitMessage,
                                    String extraMessage, Long userId) {
        if (subtitle == null || subtitle.isBlank()) {
            throw new BadRequestException("Subtitle is required — e.g. 'v1.0-initial' or 'bugfix-login'");
        }

        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        ExtractResult result;
        try {
            result = zipStorageService.extractAndStore(file, userId, projectId);
        } catch (IOException e) {
            throw new BadRequestException("Failed to extract ZIP: " + e.getMessage());
        }

        try {
            zipStorageService.updateLatestFolder(userId, projectId, result.getStoragePath());
            project.setLatestStoragePath(result.getStoragePath());
        } catch (IOException e) {
            log.warn("Could not update latest folder: {}", e.getMessage());
        }

        ZipUploadHistory history = ZipUploadHistory.builder()
                .subtitle(subtitle.trim())
                .originalFileName(result.getOriginalFileName())
                .fileSizeBytes(result.getFileSizeBytes())
                .totalFilesExtracted(result.getTotalFiles())
                .commitMessage(commitMessage)   // NEW
                .extraMessage(extraMessage)
                .storagePath(result.getStoragePath())
                .reviewStatus(ZipUploadHistory.ReviewStatus.PENDING)
                .zipProject(project)
                .user(user)
                .build();

        ZipUploadHistory savedHistory = zipUploadHistoryRepository.save(history);

        project.setUploadCount(project.getUploadCount() + 1);
        zipProjectRepository.save(project);

        log.info("ZIP uploaded: project={} subtitle='{}' files={} user={}",
                projectId, subtitle, result.getTotalFiles(), userId);

        return UploadResponse.builder()
                .historyId(savedHistory.getId())
                .subtitle(savedHistory.getSubtitle())
                .originalFileName(savedHistory.getOriginalFileName())
                .totalFilesExtracted(savedHistory.getTotalFilesExtracted())
                .storagePath(savedHistory.getStoragePath())
                .reviewStatus(savedHistory.getReviewStatus().name())
                .message("ZIP extracted successfully. " + result.getTotalFiles()
                        + " files stored. " + result.getSkippedFiles() + " files skipped.")
                .uploadedAt(savedHistory.getUploadedAt())
                .build();
    }

    @Transactional
    public void deleteUploadHistory(Long projectId, Long historyId, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        ZipUploadHistory history = zipUploadHistoryRepository
                .findByIdAndZipProject(historyId, project)
                .orElseThrow(() -> new BadRequestException("Upload history not found"));

        zipStorageService.deleteUploadFolder(history.getStoragePath());

        project.setUploadCount(Math.max(0, project.getUploadCount() - 1));
        zipProjectRepository.save(project);

        zipUploadHistoryRepository.delete(history);
        log.info("Upload history deleted: {} from project: {}", historyId, projectId);
    }

    @Transactional
    public UploadHistoryResponse getUploadHistory(Long projectId, Long historyId, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        ZipUploadHistory history = zipUploadHistoryRepository
                .findByIdAndZipProject(historyId, project)
                .orElseThrow(() -> new BadRequestException("Upload history not found"));

        return toHistoryResponse(history, project);
    }


    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    private ProjectResponse toProjectResponse(ZipProject p) {
        ZipUploadHistory latest = zipUploadHistoryRepository
                .findTopByZipProjectOrderByUploadedAtDesc(p).orElse(null);

        return ProjectResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .uploadCount(p.getUploadCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .latestUploadSubtitle(latest != null ? latest.getSubtitle() : null)
                .latestUploadAt(latest != null ? latest.getUploadedAt() : null)
                .build();
    }

    private UploadHistoryResponse toHistoryResponse(ZipUploadHistory h, ZipProject p) {
        return UploadHistoryResponse.builder()
                .id(h.getId())
                .subtitle(h.getSubtitle())
                .originalFileName(h.getOriginalFileName())
                .fileSizeBytes(h.getFileSizeBytes())
                .totalFilesExtracted(h.getTotalFilesExtracted())
                .commitMessage(h.getCommitMessage())   // NEW
                .extraMessage(h.getExtraMessage())
                .storagePath(h.getStoragePath())
                .reviewStatus(h.getReviewStatus().name())
                .uploadedAt(h.getUploadedAt())
                .projectId(p.getId())
                .projectTitle(p.getTitle())
                .build();
    }
}