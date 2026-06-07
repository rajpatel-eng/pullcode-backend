package com.capstoneproject.codereviewsystem.services.pr;

import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.CommitReviewResponse;
import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.ErrorItem;
import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.FileReviewItem;
import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Slf4j
@Service
public class GitPrCommentService {

    private final RestClient    restClient = RestClient.create();
    private final ObjectMapper  mapper     = new ObjectMapper();


    public void postReviewComment(CodeRepository repo,
                                  String decryptedToken,
                                  String commitId,
                                  CommitReviewResponse review) {

        if (decryptedToken == null || decryptedToken.isBlank()) {
            log.warn("GitPrCommentService: no token for repo {} — skipping", repo.getId());
            return;
        }
        if (review.getTotalErrors() == 0) {
            log.info("GitPrCommentService: 0 errors — skipping for commit {}", commitId);
            return;
        }

        try {
            switch (repo.getProvider()) {
                case GITHUB    -> handleGitHub(repo.getRepoUrl(),    decryptedToken, commitId, review);
                case GITLAB    -> handleGitLab(repo.getRepoUrl(),    decryptedToken, commitId, review);
                case BITBUCKET -> handleBitbucket(repo.getRepoUrl(), decryptedToken, commitId, review);
            }
        } catch (Exception e) {
            log.error("GitPrCommentService: failed for repo={} commit={}: {}",
                    repo.getId(), commitId, e.getMessage());
        }
    }

    private void handleGitHub(String repoUrl, String token,
                               String commitSha, CommitReviewResponse review) {

        OwnerRepo or = parseUrl(repoUrl, "github\\.com");
        String base = "https://api.github.com/repos/" + or.owner() + "/" + or.repo();

        Integer prNumber = findGitHubPrForCommit(base, token, commitSha);
        if (prNumber == null) {
            log.info("GitPrCommentService: no open GitHub PR for commit {} — falling back to commit comment", commitSha);
            postGitHubCommitComment(base, token, commitSha, buildSummaryComment(review));
            return;
        }

        String diff = fetchGitHubPrDiff(base, token, prNumber);

        Map<String, Map<Integer, Integer>> posMap = parseDiffPositions(diff);

        List<Map<String, Object>> comments  = new ArrayList<>();
        List<ErrorItem>           unmapped  = new ArrayList<>();

        if (review.getFiles() != null) {
            for (FileReviewItem file : review.getFiles()) {
                if (file.getErrors() == null) continue;
                Map<Integer, Integer> lineToPos = posMap.getOrDefault(file.getFilePath(), Map.of());
                for (ErrorItem err : file.getErrors()) {
                    Integer pos = (err.getLine() > 0) ? lineToPos.get(err.getLine()) : null;
                    if (pos != null) {
                        comments.add(Map.of(
                            "path",     file.getFilePath(),
                            "position", pos,
                            "body",     formatInlineComment(err)
                        ));
                    } else {
                        unmapped.add(err);
                    }
                }
            }
        }

        if (!comments.isEmpty()) {
            Map<String, Object> reviewBody = new LinkedHashMap<>();
            reviewBody.put("commit_id", commitSha);
            reviewBody.put("event",     "COMMENT");
            reviewBody.put("body",      buildSummaryComment(review) + buildUnmappedBlock(unmapped));
            reviewBody.put("comments",  comments);

            restClient.post()
                    .uri(base + "/pulls/" + prNumber + "/reviews")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT,        "application/vnd.github+json")
                    .header("X-GitHub-Api-Version",    "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reviewBody)
                    .retrieve()
                    .toBodilessEntity();

            log.info("GitPrCommentService: GitHub PR #{} — {} inline + {} unmapped comments posted",
                    prNumber, comments.size(), unmapped.size());
        } else {
            String body = buildSummaryComment(review);
            restClient.post()
                    .uri(base + "/issues/" + prNumber + "/comments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT,        "application/vnd.github+json")
                    .header("X-GitHub-Api-Version",    "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", body))
                    .retrieve()
                    .toBodilessEntity();

            log.info("GitPrCommentService: GitHub PR #{} — summary-only comment posted (no diff positions matched)", prNumber);
        }
    }

    private Integer findGitHubPrForCommit(String base, String token, String sha) {
        try {
            String raw = restClient.get()
                    .uri(base + "/commits/" + sha + "/pulls")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT,        "application/vnd.github+json")
                    .header("X-GitHub-Api-Version",    "2022-11-28")
                    .retrieve()
                    .body(String.class);

            JsonNode arr = mapper.readTree(raw);
            for (JsonNode pr : arr) {
                String state = pr.path("state").asText("");
                if ("open".equals(state)) {
                    return pr.path("number").asInt();
                }
            }
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not look up GitHub PRs for {}: {}", sha, e.getMessage());
        }
        return null;
    }

    private String fetchGitHubPrDiff(String base, String token, int prNumber) {
        try {
            return restClient.get()
                    .uri(base + "/pulls/" + prNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT,        "application/vnd.github.v3.diff")
                    .header("X-GitHub-Api-Version",    "2022-11-28")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not fetch GitHub diff for PR #{}: {}", prNumber, e.getMessage());
            return "";
        }
    }

    private void postGitHubCommitComment(String base, String token, String sha, String body) {
        try {
            restClient.post()
                    .uri(base + "/commits/" + sha + "/comments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT,        "application/vnd.github+json")
                    .header("X-GitHub-Api-Version",    "2022-11-28")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("body", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("GitPrCommentService: GitHub commit comment fallback failed: {}", e.getMessage());
        }
    }

    private void handleGitLab(String repoUrl, String token,
                               String commitSha, CommitReviewResponse review) {

        OwnerRepo or        = parseUrl(repoUrl, "gitlab\\.com");
        String    projectId = or.owner() + "%2F" + or.repo();
        String    base      = "https://gitlab.com/api/v4/projects/" + projectId;

        GitLabMrInfo mr = findGitLabMrForCommit(base, token, commitSha);
        if (mr == null) {
            log.info("GitPrCommentService: no open GitLab MR for commit {} — posting commit comment", commitSha);
            postGitLabCommitComment(base, token, commitSha, buildSummaryComment(review));
            return;
        }

        String diff = fetchGitLabMrDiff(base, token, mr.iid());

        Map<String, Map<Integer, Integer>> posMap = parseDiffPositions(diff);

        List<ErrorItem> unmapped = new ArrayList<>();
        int             pinned   = 0;

        if (review.getFiles() != null) {
            for (FileReviewItem file : review.getFiles()) {
                if (file.getErrors() == null) continue;
                Map<Integer, Integer> lineToPos = posMap.getOrDefault(file.getFilePath(), Map.of());
                for (ErrorItem err : file.getErrors()) {
                    if (err.getLine() > 0 && lineToPos.containsKey(err.getLine())) {
                        try {
                            Map<String, Object> position = new LinkedHashMap<>();
                            position.put("base_sha",  mr.baseSha());
                            position.put("start_sha", mr.startSha());
                            position.put("head_sha",  mr.headSha());
                            position.put("position_type", "text");
                            position.put("new_path",  file.getFilePath());
                            position.put("new_line",  err.getLine());

                            restClient.post()
                                    .uri(base + "/merge_requests/" + mr.iid() + "/discussions")
                                    .header("PRIVATE-TOKEN", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(Map.of(
                                        "body",     formatInlineComment(err),
                                        "position", position
                                    ))
                                    .retrieve()
                                    .toBodilessEntity();
                            pinned++;
                        } catch (RestClientResponseException ex) {
                            // GitLab rejects positions outside the visible diff — fall back gracefully
                            log.debug("GitPrCommentService: GitLab inline rejected for {}:{} — {}", file.getFilePath(), err.getLine(), ex.getStatusCode());
                            unmapped.add(err);
                        }
                    } else {
                        unmapped.add(err);
                    }
                }
            }
        }

        String summaryBody = buildSummaryComment(review) + buildUnmappedBlock(unmapped);
        restClient.post()
                .uri(base + "/merge_requests/" + mr.iid() + "/notes")
                .header("PRIVATE-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("body", summaryBody))
                .retrieve()
                .toBodilessEntity();

        log.info("GitPrCommentService: GitLab MR !{} — {} inline + {} unmapped comments posted",
                mr.iid(), pinned, unmapped.size());
    }

    private GitLabMrInfo findGitLabMrForCommit(String base, String token, String sha) {
        try {
            String raw = restClient.get()
                    .uri(base + "/repository/commits/" + sha + "/merge_requests?state=opened")
                    .header("PRIVATE-TOKEN", token)
                    .retrieve()
                    .body(String.class);

            JsonNode arr = mapper.readTree(raw);
            if (arr.isArray() && arr.size() > 0) {
                JsonNode mr     = arr.get(0);
                int      iid    = mr.path("iid").asInt();
                String   head   = mr.path("sha").asText(sha);
                String   target = mr.path("target_branch").asText("main");

                String branchRaw = restClient.get()
                        .uri(base + "/repository/branches/" + target)
                        .header("PRIVATE-TOKEN", token)
                        .retrieve()
                        .body(String.class);
                String baseSha = mapper.readTree(branchRaw).path("commit").path("id").asText(head);

                return new GitLabMrInfo(iid, baseSha, baseSha, head);
            }
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not look up GitLab MR for {}: {}", sha, e.getMessage());
        }
        return null;
    }

    private String fetchGitLabMrDiff(String base, String token, int mrIid) {
        try {
            String raw = restClient.get()
                    .uri(base + "/merge_requests/" + mrIid + "/diffs")
                    .header("PRIVATE-TOKEN", token)
                    .retrieve()
                    .body(String.class);

            JsonNode arr = mapper.readTree(raw);
            StringBuilder sb = new StringBuilder();
            for (JsonNode d : arr) {
                sb.append("--- a/").append(d.path("old_path").asText()).append("\n");
                sb.append("+++ b/").append(d.path("new_path").asText()).append("\n");
                sb.append(d.path("diff").asText()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not fetch GitLab diff for MR !{}: {}", mrIid, e.getMessage());
            return "";
        }
    }

    private void postGitLabCommitComment(String base, String token, String sha, String body) {
        try {
            restClient.post()
                    .uri(base + "/repository/commits/" + sha + "/comments")
                    .header("PRIVATE-TOKEN", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("note", body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("GitPrCommentService: GitLab commit comment fallback failed: {}", e.getMessage());
        }
    }

    private void handleBitbucket(String repoUrl, String token,
                                  String commitSha, CommitReviewResponse review) {

        OwnerRepo or  = parseUrl(repoUrl, "bitbucket\\.org");
        String    base = "https://api.bitbucket.org/2.0/repositories/" + or.owner() + "/" + or.repo();

        Integer prId = findBitbucketPrForCommit(base, token, commitSha);
        if (prId == null) {
            log.info("GitPrCommentService: no open Bitbucket PR for commit {} — posting commit comment", commitSha);
            postBitbucketCommitComment(base, token, commitSha, buildSummaryComment(review));
            return;
        }

        String diff = fetchBitbucketPrDiff(base, token, prId);

        Map<String, Map<Integer, Integer>> posMap = parseDiffPositions(diff);

        List<ErrorItem> unmapped = new ArrayList<>();
        int             pinned   = 0;

        if (review.getFiles() != null) {
            for (FileReviewItem file : review.getFiles()) {
                if (file.getErrors() == null) continue;
                Map<Integer, Integer> lineToPos = posMap.getOrDefault(file.getFilePath(), Map.of());
                for (ErrorItem err : file.getErrors()) {
                    if (err.getLine() > 0 && lineToPos.containsKey(err.getLine())) {
                        try {
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("content", Map.of("raw", formatInlineComment(err)));
                            payload.put("inline",  Map.of(
                                "to",   err.getLine(),
                                "path", file.getFilePath()
                            ));

                            restClient.post()
                                    .uri(base + "/pullrequests/" + prId + "/comments")
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(payload)
                                    .retrieve()
                                    .toBodilessEntity();
                            pinned++;
                        } catch (RestClientResponseException ex) {
                            log.debug("GitPrCommentService: Bitbucket inline rejected for {}:{} — {}", file.getFilePath(), err.getLine(), ex.getStatusCode());
                            unmapped.add(err);
                        }
                    } else {
                        unmapped.add(err);
                    }
                }
            }
        }

        String summaryBody = buildSummaryComment(review) + buildUnmappedBlock(unmapped);
        restClient.post()
                .uri(base + "/pullrequests/" + prId + "/comments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("content", Map.of("raw", summaryBody)))
                .retrieve()
                .toBodilessEntity();

        log.info("GitPrCommentService: Bitbucket PR #{} — {} inline + {} unmapped comments posted",
                prId, pinned, unmapped.size());
    }

    private Integer findBitbucketPrForCommit(String base, String token, String sha) {
        try {
            String raw = restClient.get()
                    .uri(base + "/pullrequests?state=OPEN")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(raw);
            for (JsonNode pr : root.path("values")) {
                String prSha = pr.path("source").path("commit").path("hash").asText("");
                if (sha.startsWith(prSha) || prSha.startsWith(sha)) {
                    return pr.path("id").asInt();
                }
            }
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not look up Bitbucket PR for {}: {}", sha, e.getMessage());
        }
        return null;
    }

    private String fetchBitbucketPrDiff(String base, String token, int prId) {
        try {
            return restClient.get()
                    .uri(base + "/pullrequests/" + prId + "/diff")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("GitPrCommentService: could not fetch Bitbucket diff for PR #{}: {}", prId, e.getMessage());
            return "";
        }
    }

    private void postBitbucketCommitComment(String base, String token, String sha, String body) {
        try {
            restClient.post()
                    .uri(base + "/commit/" + sha + "/comments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", Map.of("raw", body)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("GitPrCommentService: Bitbucket commit comment fallback failed: {}", e.getMessage());
        }
    }


    private Map<String, Map<Integer, Integer>> parseDiffPositions(String diff) {
        Map<String, Map<Integer, Integer>> result = new LinkedHashMap<>();
        if (diff == null || diff.isBlank()) return result;

        String  currentFile    = null;
        int     diffPosition   = 0;   
        int     newLineNumber  = 0;   
        Pattern filePattern  = Pattern.compile("^\\+\\+\\+ b/(.+)$");
        Pattern hunkPattern  = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

        for (String line : diff.split("\n")) {
            Matcher fm = filePattern.matcher(line);
            if (fm.matches()) {
                currentFile   = fm.group(1).trim();
                diffPosition  = 0;
                newLineNumber = 0;
                result.putIfAbsent(currentFile, new LinkedHashMap<>());
                continue;
            }

            if (currentFile == null) continue;

            Matcher hm = hunkPattern.matcher(line);
            if (hm.find()) {
                newLineNumber = Integer.parseInt(hm.group(1)) - 1; 
                diffPosition++;   
                continue;
            }

            if (line.startsWith("-")) {
                diffPosition++;   
                continue;
            }
            if (line.startsWith("+") || line.startsWith(" ")) {
                diffPosition++;
                newLineNumber++;
                if (line.startsWith("+")) {
                    result.get(currentFile).put(newLineNumber, diffPosition);
                }
            }
        }
        return result;
    }


    private String formatInlineComment(ErrorItem err) {
        String sev   = err.getSeverity() != null ? err.getSeverity().name() : "UNKNOWN";
        String badge = severityBadge(sev);
        StringBuilder sb = new StringBuilder();
        sb.append(badge).append(" **").append(sev).append("**");
        if (err.getRuleId() != null && !err.getRuleId().isBlank()) {
            sb.append(" `").append(err.getRuleId()).append("`");
        }
        sb.append("\n\n").append(err.getMessage());
        if (err.getSuggestion() != null && !err.getSuggestion().isBlank()) {
            sb.append("\n\n> 💡 ").append(err.getSuggestion());
        }
        return sb.toString();
    }

    private String buildSummaryComment(CommitReviewResponse review) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 AI Code Review — `").append(review.getCommitId()).append("`\n\n");
        sb.append("| Metric | Value |\n|---|---|\n");
        sb.append("| Total files       | ").append(review.getTotalFiles()).append(" |\n");
        sb.append("| Reviewed by AI    | ").append(review.getFilesSentToAi()).append(" |\n");
        sb.append("| Total issues      | **").append(review.getTotalErrors()).append("** |\n");

        if (review.getErrorsBySeverity() != null) {
            for (Map.Entry<String, Long> e : review.getErrorsBySeverity().entrySet()) {
                sb.append("| ").append(severityBadge(e.getKey()))
                  .append(" ").append(e.getKey())
                  .append(" | ").append(e.getValue()).append(" |\n");
            }
        }
        sb.append("\n*Inline comments have been posted on the relevant diff lines.*\n");
        return sb.toString();
    }

    private String buildUnmappedBlock(List<ErrorItem> unmapped) {
        if (unmapped == null || unmapped.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n### Issues not in diff\n\n");
        for (ErrorItem err : unmapped) {
            String sev = err.getSeverity() != null ? err.getSeverity().name() : "UNKNOWN";
            sb.append("- ").append(severityBadge(sev)).append(" **").append(sev).append("**");
            if (err.getLine() > 0) sb.append(" `L").append(err.getLine()).append("`");
            sb.append(" — ").append(err.getMessage()).append("\n");
            if (err.getSuggestion() != null && !err.getSuggestion().isBlank()) {
                sb.append("  > 💡 ").append(err.getSuggestion()).append("\n");
            }
        }
        return sb.toString();
    }


    private OwnerRepo parseUrl(String url, String hostPattern) {
        Matcher m = Pattern.compile(hostPattern + "[:/]([^/]+)/([^/.]+)").matcher(url);
        if (!m.find()) throw new IllegalArgumentException("Cannot parse git URL: " + url);
        return new OwnerRepo(m.group(1), m.group(2));
    }

    private String severityBadge(String sev) {
        return switch (sev) {
            case "CRITICAL" -> "🔴";
            case "HIGH"     -> "🟠";
            case "MEDIUM"   -> "🟡";
            case "LOW"      -> "🔵";
            default         -> "⚪";
        };
    }


    private record OwnerRepo(String owner, String repo) {}
    private record GitLabMrInfo(int iid, String baseSha, String startSha, String headSha) {}
}