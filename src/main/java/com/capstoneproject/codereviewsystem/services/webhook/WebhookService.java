package com.capstoneproject.codereviewsystem.services.webhook;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.CommitHistory;
import com.capstoneproject.codereviewsystem.entity.CommitHistory.ReviewStatus;
import com.capstoneproject.codereviewsystem.repos.CodeRepositoryRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.services.storage.GitProviderFileService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final CodeRepositoryRepository repoRepository;
    private final CommitHistoryRepository commitHistoryRepository;
    private final GitProviderFileService gitProviderFileService;

    // ─── GitHub ───────────────────────────────────────────────────────────────

    @Transactional
    public void processGithubPush(JsonNode root, String signature, String rawPayload) {
        String repoUrl = root.path("repository").path("html_url").asText();
        String branch  = root.path("ref").asText().replace("refs/heads/", "");

        // All users who registered this repo URL
        List<CodeRepository> repos = repoRepository.findAllByRepoUrl(repoUrl);
        if (repos.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        // Find the one whose webhookSecret matches the incoming signature
        // This supports multiple users registering the same repo — each has a unique secret
        List<CodeRepository> matched = repos.stream()
                .filter(r -> verifyGithubSignature(rawPayload, signature, r.getWebhookSecret()))
                .toList();

        if (matched.isEmpty()) {
            log.warn("GitHub signature did not match any registration for URL: {}", repoUrl);
            return;
        }

        for (CodeRepository repo : matched) {
            JsonNode commits = root.path("commits");
            for (JsonNode commit : commits)
                saveCommitAndFetchFullProject(repo, commit, branch, "github");
            log.info("Processed {} commits for repo: {} (user: {})",
                    commits.size(), repoUrl, repo.getUser().getEmail());
        }
    }

    // ─── GitLab ───────────────────────────────────────────────────────────────

    @Transactional
    public void processGitlabPush(JsonNode root, String token) {
        String repoUrl = root.path("project").path("web_url").asText();
        String branch  = root.path("ref").asText().replace("refs/heads/", "");

        List<CodeRepository> repos = repoRepository.findAllByRepoUrl(repoUrl);
        if (repos.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        // For GitLab, the token is a plain string match — find repos where secret == token
        List<CodeRepository> matched = repos.stream()
                .filter(r -> r.getWebhookSecret() == null
                          || r.getWebhookSecret().isBlank()
                          || r.getWebhookSecret().equals(token))
                .toList();

        if (matched.isEmpty()) {
            log.warn("GitLab token did not match any registration for URL: {}", repoUrl);
            return;
        }

        for (CodeRepository repo : matched) {
            JsonNode commits = root.path("commits");
            for (JsonNode commit : commits)
                saveCommitAndFetchFullProject(repo, commit, branch, "gitlab");
            log.info("Processed {} commits for repo: {} (user: {})",
                    commits.size(), repoUrl, repo.getUser().getEmail());
        }
    }

    // ─── Bitbucket ────────────────────────────────────────────────────────────

    @Transactional
    public void processBitbucketPush(JsonNode root) {
        String repoUrl = root.path("repository").path("links").path("html").path("href").asText();

        List<CodeRepository> repos = repoRepository.findAllByRepoUrl(repoUrl);
        if (repos.isEmpty()) {
            log.warn("No registered repo found for URL: {}", repoUrl);
            return;
        }

        // Bitbucket doesn't send per-registration secrets in the same way,
        // so we fan out to all users who registered this repo
        for (CodeRepository repo : repos) {
            JsonNode changes = root.path("push").path("changes");
            for (JsonNode change : changes) {
                String branch  = change.path("new").path("name").asText();
                JsonNode commits = change.path("commits");
                for (JsonNode commit : commits)
                    saveCommitAndFetchFullProject(repo, commit, branch, "bitbucket");
            }
        }
    }

    // ─── Core: save commit + trigger file fetch ────────────────────────────────

    private void saveCommitAndFetchFullProject(CodeRepository repo, JsonNode commit,
                                                String branch, String provider) {

        String commitId = provider.equals("bitbucket")
                ? commit.path("hash").asText()
                : commit.path("id").asText();

        if (commitId.isBlank()) {
            log.warn("Blank commitId for provider: {}", provider);
            return;
        }

        // Skip duplicate per user-repo pair
        if (commitHistoryRepository.findByCommitIdAndRepository(commitId, repo).isPresent()) {
            log.debug("Duplicate commit skipped: {} for repo: {}", commitId, repo.getId());
            return;
        }

        // Previous commit for this repo (to delete its snapshot)
        String previousCommitId = commitHistoryRepository
                .findTopByRepositoryOrderByReceivedAtDesc(repo)
                .map(CommitHistory::getCommitId)
                .orElse(null);

        // Parse file metadata
        List<String> filesChanged = new ArrayList<>();
        int added = 0, modified = 0, removed = 0;

        if (provider.equals("github") || provider.equals("gitlab")) {
            added    = countFiles(commit.path("added"),    filesChanged);
            modified = countFiles(commit.path("modified"), filesChanged);
            removed  = countFiles(commit.path("removed"),  filesChanged);
        } else {
            filesChanged.add("(see stored snapshot)");
        }

        // Author
        String authorName, authorEmail;
        if (provider.equals("bitbucket")) {
            authorName  = commit.path("author").path("user").path("display_name").asText();
            authorEmail = "";
        } else {
            authorName  = commit.path("author").path("name").asText();
            authorEmail = commit.path("author").path("email").asText();
        }

        // Timestamp
        String tsField = provider.equals("bitbucket")
                ? commit.path("date").asText()
                : commit.path("timestamp").asText();
        LocalDateTime committedAt = parseTimestamp(tsField);

        String repoName    = extractRepoName(repo.getRepoUrl());
        String storagePath = "uploads/webhook/" + repoName + "/" + commitId;

        CommitHistory history = CommitHistory.builder()
                .commitId(commitId)
                .commitMessage(commit.path("message").asText())
                .commitUrl(commit.path("url").asText())
                .authorName(authorName)
                .authorEmail(authorEmail)
                .branch(branch)
                .filesChanged(filesChanged.toString())
                .filesAddedCount(added)
                .filesModifiedCount(modified)
                .filesRemovedCount(removed)
                .reviewStatus(ReviewStatus.PENDING)
                .repository(repo)
                .user(repo.getUser())
                .committedAt(committedAt)
                .storagePath(storagePath)
                .build();

        commitHistoryRepository.save(history);
        log.info("Commit saved: {} | {} | +{} ~{} -{} | user: {}",
                commitId.substring(0, Math.min(7, commitId.length())),
                commit.path("message").asText(),
                added, modified, removed,
                repo.getUser().getEmail());

        fetchFullProjectAsync(repo, commitId, previousCommitId);
    }

    @Async
    public void fetchFullProjectAsync(CodeRepository repo, String commitId,
                                       String previousCommitId) {
        log.info("Async fetch started | commit: {} | removing: {}",
                commitId, previousCommitId != null ? previousCommitId : "none");
        gitProviderFileService.fetchAndStoreFullProject(repo, commitId, previousCommitId);
        log.info("Async fetch completed | commit: {}", commitId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int countFiles(JsonNode filesNode, List<String> filesChanged) {
        if (filesNode.isArray()) {
            filesNode.forEach(f -> filesChanged.add(f.asText()));
            return filesNode.size();
        }
        return 0;
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        if (parts.length >= 2)
            return parts[parts.length - 2] + "_" + parts[parts.length - 1];
        return "unknown_repo";
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(timestamp.substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private boolean verifyGithubSignature(String payload, String signature, String secret) {
        if (secret == null || secret.isBlank()) return true;
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++)
            result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }
}