package com.capstoneproject.codereviewsystem.services.zip;

import com.capstoneproject.codereviewsystem.dtos.ZipProjectDtos.*;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.ProjectCommitRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.repos.ZipProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ZipProjectService {

    private final ZipProjectRepository zipProjectRepository;
    private final ProjectCommitRepository commitRepository;
    private final ZipStorageService zipStorageService;
    private final UserRepository userRepository;
    private final AiModelRepository aiModelRepository;   // ← NEW

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
                .commitCount(0)
                .build();

        if (req.getAiModelId() != null) {
            AiModel model = aiModelRepository.findById(req.getAiModelId())
                    .orElseThrow(() -> new BadRequestException("AI model not found: " + req.getAiModelId()));
            if (!model.isActive() || model.isDeleted()) {
                throw new BadRequestException("AI model is not active: " + req.getAiModelId());
            }
            project.setAiModel(model);
        }

        ZipProject saved = zipProjectRepository.save(project);
        log.info("Project created: '{}' by user: {} aiModel={}",
                saved.getTitle(), userId,
                saved.getAiModel() != null ? saved.getAiModel().getId() : "none");
        return toProjectResponse(saved);
    }

    public List<ProjectResponse> getAllProjects(Long userId) {
        User user = getUser(userId);
        return zipProjectRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toProjectResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProject(Long projectId, Long userId) {
        User user = getUser(userId);
        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));
        return toProjectResponse(project);
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

        if (req.isAiModelFieldPresent()) {
            if (req.getAiModelId() != null) {
                AiModel model = aiModelRepository.findById(req.getAiModelId())
                        .orElseThrow(() -> new BadRequestException(
                                "AI model not found: " + req.getAiModelId()));
                if (!model.isActive() || model.isDeleted()) {
                    throw new BadRequestException("AI model is not active: " + req.getAiModelId());
                }
                project.setAiModel(model);
            } else {
                project.setAiModel(null); // explicitly remove
            }
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
        log.info("Project deleted: {} by user: {}", projectId, userId);
    }


    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    private ProjectResponse toProjectResponse(ZipProject p) {
        var latest = commitRepository
                .findByZipProjectIdOrderByCommittedAtDesc(
                        p.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().stream().findFirst().orElse(null);

        return ProjectResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .description(p.getDescription())
                .commitCount(p.getCommitCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .aiModelId(p.getAiModel() != null ? p.getAiModel().getId() : null)
                .aiModelName(p.getAiModel() != null ? p.getAiModel().getName() : null)
                .latestCommitMessage(latest != null ? latest.getCommitMessage() : null)
                .latestCommitAt(latest != null ? latest.getCommittedAt() : null)
                .build();
    }
}
