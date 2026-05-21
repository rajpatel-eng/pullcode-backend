package com.capstoneproject.codereviewsystem.services.repo;

import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryRequest;
import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryResponse;
import com.capstoneproject.codereviewsystem.dtos.CommitHistoryResponse;
import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.CodeRepository.RepoProvider;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
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

    @Transactional
    public CodeRepositoryResponse addRepository(CodeRepositoryRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (repoRepository.existsByRepoUrlAndUser(request.getRepoUrl(), user)) {
            throw new BadRequestException("You already added this repository");
        }

        RepoProvider provider = detectProvider(request.getRepoUrl());

        CodeRepository repo = CodeRepository.builder()
                .title(request.getTitle())
                .repoUrl(request.getRepoUrl())
                .provider(provider)
                .accessToken(request.getAccessToken())
                .defaultBranch(request.getBranch() != null ? request.getBranch() : "main")
                .webhookSecret(UUID.randomUUID().toString()) // generated, sent to GitHub
                .user(user)
                .build();

        repoRepository.save(repo);
        log.info("Repo added: {} by user: {}", request.getRepoUrl(), userId);

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

    private RepoProvider detectProvider(String repoUrl) {
        if (repoUrl.contains("github.com"))    return RepoProvider.GITHUB;
        if (repoUrl.contains("gitlab.com"))    return RepoProvider.GITLAB;
        if (repoUrl.contains("bitbucket.org")) return RepoProvider.BITBUCKET;
        throw new BadRequestException("Unsupported provider. Use GitHub, GitLab, or Bitbucket.");
    }

    private CodeRepositoryResponse toResponse(CodeRepository repo) {
        return CodeRepositoryResponse.builder()
                .id(repo.getId())
                .title(repo.getTitle())
                .repoUrl(repo.getRepoUrl())
                .provider(repo.getProvider())
                .hasAccessToken(repo.getAccessToken() != null)
                .webhookStatus(repo.getWebhookId() != null ? "ACTIVE" : "NOT_CONFIGURED")
                .createdAt(repo.getCreatedAt())
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