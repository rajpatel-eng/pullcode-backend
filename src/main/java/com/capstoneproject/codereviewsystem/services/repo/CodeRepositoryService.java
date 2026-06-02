package com.capstoneproject.codereviewsystem.services.repo;

import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryRequest;
import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryResponse;
import com.capstoneproject.codereviewsystem.dtos.CommitHistoryResponse;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.CodeRepository.RepoProvider;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.CodeRepositoryRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeRepositoryService {

    private final CodeRepositoryRepository repoRepository;
    private final CommitHistoryRepository commitHistoryRepository;
    private final UserRepository userRepository;
    private final AiModelRepository aiModelRepository;   // NEW

    @Transactional
    public CodeRepositoryResponse addRepository(CodeRepositoryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (repoRepository.existsByRepoUrlAndUser(request.getRepoUrl(), user)) {
            throw new BadRequestException("You already added this repository");
        }

        RepoProvider provider = detectProvider(request.getRepoUrl());
        String webhookSecret = UUID.randomUUID().toString();
        String webhookUrl = buildWebhookUrl(provider);

        AiModel aiModel = resolveAiModel(request.getAiModelId());

        CodeRepository repo = CodeRepository.builder()
                .title(request.getTitle())
                .repoUrl(request.getRepoUrl())
                .provider(provider)
                .accessToken(request.getAccessToken())
                .defaultBranch(request.getBranch() != null ? request.getBranch() : "main")
                .webhookSecret(webhookSecret)
                .user(user)
                .aiModel(aiModel)        // NEW
                .build();

        repoRepository.save(repo);
        log.info("Repo added: {} by user: {} with model: {}",
                request.getRepoUrl(), userId,
                aiModel != null ? aiModel.getName() : "none");

        return CodeRepositoryResponse.builder()
                .id(repo.getId())
                .title(repo.getTitle())
                .repoUrl(repo.getRepoUrl())
                .provider(repo.getProvider())
                .hasAccessToken(repo.getAccessToken() != null && !repo.getAccessToken().isBlank())
                .webhookStatus("NOT_CONFIGURED")
                .createdAt(repo.getCreatedAt())
                .webhookSecret(webhookSecret)
                .webhookUrl(webhookUrl)
                .aiModelId(aiModel != null ? aiModel.getId() : null)
                .aiModelName(aiModel != null ? aiModel.getName() : null)
                .aiModelProvider(aiModel != null ? aiModel.getProvider() : null)
                .build();
    }


    @Transactional
    public CodeRepositoryResponse updateAiModel(Long repoId, Long aiModelId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        AiModel newModel = aiModelRepository.findByIdAndActiveTrueAndDeletedFalse(aiModelId)
                .orElseThrow(() -> new BadRequestException(
                        "AI model not found or is not currently active"));

        repo.setAiModel(newModel);
        repoRepository.save(repo);
        log.info("Repo {} AI model updated to {} by user {}", repoId, newModel.getName(), userId);

        return toResponse(repo);
    }

    @Transactional
    public CodeRepositoryResponse updateAccessToken(Long repoId, String accessToken, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        String token = (accessToken != null && !accessToken.isBlank()) ? accessToken : null;
        repo.setAccessToken(token);
        repoRepository.save(repo);

        log.info("Access token {} for repo: {}", token != null ? "updated" : "removed", repoId);
        return toResponse(repo);
    }

    public List<CodeRepositoryResponse> getMyRepositories(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        return repoRepository.findByUser(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public CodeRepositoryResponse getRepository(Long repoId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        return toResponse(repo);
    }

    @Transactional
    public void deleteRepository(Long repoId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        repoRepository.delete(repo);
        log.info("Repo deleted: {} by user: {}", repoId, userId);
    }

    public Page<CommitHistoryResponse> getCommitHistory(
            Long repoId, Long userId, Pageable pageable) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        return commitHistoryRepository
                .findByRepositoryOrderByCommittedAtDesc(repo, pageable)
                .map(this::toCommitResponse);
    }

    public Page<CommitHistoryResponse> getAllMyCommits(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        return commitHistoryRepository
                .findByUserOrderByCommittedAtDesc(user, pageable)
                .map(this::toCommitResponse);
    }

    public Page<CommitHistoryResponse> getCommitsByBranch(
            Long repoId, String branch, Long userId, Pageable pageable) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        CodeRepository repo = repoRepository.findByIdAndUser(repoId, user)
                .orElseThrow(() -> new BadRequestException("Repository not found"));

        return commitHistoryRepository
                .findByRepositoryAndBranchOrderByCommittedAtDesc(repo, branch, pageable)
                .map(this::toCommitResponse);
    }

    private AiModel resolveAiModel(Long aiModelId) {
        if (aiModelId != null) {
            return aiModelRepository.findByIdAndActiveTrueAndDeletedFalse(aiModelId)
                    .orElseThrow(() -> new BadRequestException(
                            "Selected AI model is not available. Please choose an active model."));
        }
        return aiModelRepository.findByDefaultModelTrueAndDeletedFalse().orElse(null);
    }

    private RepoProvider detectProvider(String repoUrl) {
        if (repoUrl.contains("github.com"))    return RepoProvider.GITHUB;
        if (repoUrl.contains("gitlab.com"))    return RepoProvider.GITLAB;
        if (repoUrl.contains("bitbucket.org")) return RepoProvider.BITBUCKET;
        throw new BadRequestException("Unsupported provider. Use GitHub, GitLab, or Bitbucket.");
    }

    private String buildWebhookUrl(RepoProvider provider) {
        String base = "https://YOUR-PUBLIC-URL";
        return switch (provider) {
            case GITHUB    -> base + "/api/webhook/github";
            case GITLAB    -> base + "/api/webhook/gitlab";
            case BITBUCKET -> base + "/api/webhook/bitbucket";
        };
    }

    private CodeRepositoryResponse toResponse(CodeRepository repo) {
        AiModel model = repo.getAiModel();
        return CodeRepositoryResponse.builder()
                .id(repo.getId())
                .title(repo.getTitle())
                .repoUrl(repo.getRepoUrl())
                .provider(repo.getProvider())
                .hasAccessToken(repo.getAccessToken() != null && !repo.getAccessToken().isBlank())
                .webhookStatus(repo.getWebhookId() != null ? "ACTIVE" : "NOT_CONFIGURED")
                .createdAt(repo.getCreatedAt())
                .aiModelId(model != null ? model.getId() : null)
                .aiModelName(model != null ? model.getName() : null)
                .aiModelProvider(model != null ? model.getProvider() : null)
                // webhookSecret and webhookUrl intentionally NOT set here
                .build();
    }

    private CommitHistoryResponse toCommitResponse(CommitHistory commit) {
        return CommitHistoryResponse.builder()
                .id(commit.getId())
                .commitId(commit.getCommitId())
                .commitMessage(commit.getCommitMessage())
                .commitUrl(commit.getCommitUrl())
                .authorName(commit.getAuthorName())
                .branch(commit.getBranch())
                .filesChanged(commit.getFilesChanged())
                .filesAddedCount(commit.getFilesAddedCount())
                .filesModifiedCount(commit.getFilesModifiedCount())
                .filesRemovedCount(commit.getFilesRemovedCount())
                .reviewStatus(commit.getReviewStatus())
                .committedAt(commit.getCommittedAt())
                .receivedAt(commit.getReceivedAt())
                .repositoryId(commit.getRepository().getId())
                .repositoryTitle(commit.getRepository().getTitle())
                .repoUrl(commit.getRepository().getRepoUrl())
                .build();
    }
}