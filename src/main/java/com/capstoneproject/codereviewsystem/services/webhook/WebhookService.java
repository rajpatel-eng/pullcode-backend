package com.capstoneproject.codereviewsystem.services.webhook;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.CommitHistory.ReviewStatus;
import com.capstoneproject.codereviewsystem.repos.CodeRepositoryRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final CodeRepositoryRepository repoRepository;
    private final CommitHistoryRepository commitHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processGithubPush(JsonNode root, String signature) {

        String repoUrl = root.path("repository").path("html_url").asText();
        String branch  = root.path("ref").asText().replace("refs/heads/", "");

        Optional<CodeRepository> repoOpt = repoRepository
                .findByRepoUrlContaining(repoUrl);

        if (repoOpt.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        CodeRepository repo = repoOpt.get();
        JsonNode commits = root.path("commits");

        for (JsonNode commit : commits) {
            saveCommit(repo, commit, branch, "github");
        }

        log.info("Processed {} commits for repo: {}", commits.size(), repoUrl);
    }

    @Transactional
    public void processGitlabPush(JsonNode root) {

        String repoUrl = root.path("project").path("web_url").asText();
        String branch  = root.path("ref").asText().replace("refs/heads/", "");

        Optional<CodeRepository> repoOpt = repoRepository
                .findByRepoUrlContaining(repoUrl);

        if (repoOpt.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        CodeRepository repo = repoOpt.get();
        JsonNode commits = root.path("commits");

        for (JsonNode commit : commits) {
            saveCommit(repo, commit, branch, "gitlab");
        }

        log.info("Processed {} commits for repo: {}", commits.size(), repoUrl);
    }

    @Transactional
    public void processBitbucketPush(JsonNode root) {

        String repoUrl = root.path("repository").path("links")
                .path("html").path("href").asText();

        JsonNode changes = root.path("push").path("changes");

        Optional<CodeRepository> repoOpt = repoRepository
                .findByRepoUrlContaining(repoUrl);

        if (repoOpt.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        CodeRepository repo = repoOpt.get();

        for (JsonNode change : changes) {
            String branch = change.path("new").path("name").asText();
            JsonNode commits = change.path("commits");
            for (JsonNode commit : commits) {
                saveCommit(repo, commit, branch, "bitbucket");
            }
        }
    }

    private void saveCommit(CodeRepository repo, JsonNode commit,
                             String branch, String provider) {

        String commitId = commit.path("id").asText();

        if (commitHistoryRepository.findByCommitIdAndRepository(commitId, repo).isPresent()) {
            log.debug("Duplicate commit skipped: {}", commitId);
            return;
        }

        List<String> filesChanged = new ArrayList<>();
        int added = 0, modified = 0, removed = 0;

        if (provider.equals("github") || provider.equals("gitlab")) {
            added    = addFiles(commit.path("added"), filesChanged);
            modified = addFiles(commit.path("modified"), filesChanged);
            removed  = addFiles(commit.path("removed"), filesChanged);
        } else if (provider.equals("bitbucket")) {
            filesChanged.add("(file details not available in Bitbucket webhook)");
        }

        LocalDateTime committedAt = parseTimestamp(commit.path("timestamp").asText());

        CommitHistory history = CommitHistory.builder()
                .commitId(commitId)
                .commitMessage(commit.path("message").asText())
                .commitUrl(commit.path("url").asText())
                .authorName(commit.path("author").path("name").asText())
                .authorEmail(commit.path("author").path("email").asText())
                .branch(branch)
                .filesChanged(filesChanged.toString())
                .filesAddedCount(added)
                .filesModifiedCount(modified)
                .filesRemovedCount(removed)
                .reviewStatus(ReviewStatus.PENDING)
                .repository(repo)
                .user(repo.getUser())
                .committedAt(committedAt)
                .build();

        commitHistoryRepository.save(history);
        log.info("Commit saved: {} | {} | +{} ~{} -{}",
                commitId.substring(0, Math.min(7, commitId.length())),
                commit.path("message").asText(),
                added, modified, removed);
    }

    private int addFiles(JsonNode filesNode, List<String> list) {
        if (filesNode.isArray()) {
            filesNode.forEach(f -> list.add(f.asText()));
            return filesNode.size();
        }
        return 0;
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(
                timestamp.substring(0, 19),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            );
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}