package com.capstoneproject.codereviewsystem.services.storage;

import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.services.encryption.EncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitProviderFileService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final EncryptionService encryptionService;


    public void fetchAndStoreFullProject(CodeRepository repo, String commitId,
                                         String previousCommitId) {

        String repoName = extractRepoName(repo.getRepoUrl());

        if (previousCommitId != null && !previousCommitId.isBlank()) {
            if (fileStorageService.submissionExists("webhook", repoName, previousCommitId)) {
                fileStorageService.deleteSubmission("webhook", repoName, previousCommitId);
                log.info("Deleted old snapshot: webhook/{}/{}", repoName, previousCommitId);
            }
        }

        if (fileStorageService.submissionExists("webhook", repoName, commitId)) {
            log.info("Snapshot already exists for commit: {}", commitId);
            return;
        }

        List<String> allFilePaths;
        try {
            allFilePaths = fetchAllFilePaths(repo, commitId);
        } catch (Exception e) {
            log.error("Failed to fetch file tree for commit {}: {}", commitId, e.getMessage());
            return;
        }

        if (allFilePaths.isEmpty()) {
            log.warn("No files found in tree for commit: {}", commitId);
            return;
        }

        log.info("Fetching {} files for full snapshot of commit: {}", allFilePaths.size(), commitId);

        int saved = 0;
        for (String filePath : allFilePaths) {
            try {
                String content = fetchFileContent(repo, filePath, commitId);
                if (content != null) {
                    fileStorageService.saveFile("webhook", repoName, commitId, filePath, content);
                    saved++;
                }
            } catch (Exception e) {
                log.error("Failed to fetch file {}: {}", filePath, e.getMessage());
            }
        }

        log.info("Full snapshot saved: {}/{} files at webhook/{}/{}",
                saved, allFilePaths.size(), repoName, commitId);
    }


    private List<String> fetchAllFilePaths(CodeRepository repo, String commitId) {
        return switch (repo.getProvider()) {
            case GITHUB    -> fetchAllFilePathsGitHub(repo, commitId);
            case GITLAB    -> fetchAllFilePathsGitLab(repo, commitId);
            case BITBUCKET -> fetchAllFilePathsBitbucket(repo, commitId);
        };
    }

    private List<String> fetchAllFilePathsGitHub(CodeRepository repo, String commitId) {
        List<String> paths = new ArrayList<>();
        try {
            String ownerRepo = extractOwnerRepo(repo.getRepoUrl(), "github.com");
            // recursive tree gives ALL files in one call
            String apiUrl = String.format(
                    "https://api.github.com/repos/%s/git/trees/%s?recursive=1",
                    ownerRepo, commitId
            );

            HttpHeaders headers = buildGitHubHeaders(repo);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode tree = root.path("tree");
            for (JsonNode node : tree) {
                if ("blob".equals(node.path("type").asText())) {
                    paths.add(node.path("path").asText());
                }
            }
            log.debug("GitHub tree returned {} files for commit {}", paths.size(), commitId);
        } catch (Exception e) {
            log.error("GitHub tree fetch failed: {}", e.getMessage());
        }
        return paths;
    }

    private List<String> fetchAllFilePathsGitLab(CodeRepository repo, String commitId) {
        List<String> paths = new ArrayList<>();
        try {
            String projectPath = extractOwnerRepo(repo.getRepoUrl(), "gitlab.com");
            String encodedProject = projectPath.replace("/", "%2F");
            // GitLab list-tree with recursive=true, paginated — fetch first page (100 items)
            String apiUrl = String.format(
                    "https://gitlab.com/api/v4/projects/%s/repository/tree?ref=%s&recursive=true&per_page=100",
                    encodedProject, commitId
            );

            HttpHeaders headers = new HttpHeaders();
            String token = decryptToken(repo);
            if (token != null) {
                headers.set("PRIVATE-TOKEN", token);
            }

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );

            JsonNode tree = objectMapper.readTree(response.getBody());
            for (JsonNode node : tree) {
                if ("blob".equals(node.path("type").asText())) {
                    paths.add(node.path("path").asText());
                }
            }
        } catch (Exception e) {
            log.error("GitLab tree fetch failed: {}", e.getMessage());
        }
        return paths;
    }

    private List<String> fetchAllFilePathsBitbucket(CodeRepository repo, String commitId) {
        List<String> paths = new ArrayList<>();
        try {
            String ownerRepo = extractOwnerRepo(repo.getRepoUrl(), "bitbucket.org");
            // Bitbucket src endpoint with pagelen=100
            String apiUrl = String.format(
                    "https://api.bitbucket.org/2.0/repositories/%s/src/%s/?pagelen=100",
                    ownerRepo, commitId
            );

            HttpHeaders headers = new HttpHeaders();
            String token = decryptToken(repo);
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }

            collectBitbucketPaths(apiUrl, headers, paths, ownerRepo, commitId);
        } catch (Exception e) {
            log.error("Bitbucket tree fetch failed: {}", e.getMessage());
        }
        return paths;
    }

    private void collectBitbucketPaths(String url, HttpHeaders headers,
                                        List<String> paths, String ownerRepo, String commitId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode values = root.path("values");
            for (JsonNode node : values) {
                String type = node.path("type").asText();
                String path = node.path("path").asText();
                if ("commit_file".equals(type)) {
                    paths.add(path);
                } else if ("commit_directory".equals(type)) {
                    // recurse into subdirectory
                    String subUrl = String.format(
                            "https://api.bitbucket.org/2.0/repositories/%s/src/%s/%s/?pagelen=100",
                            ownerRepo, commitId, path
                    );
                    collectBitbucketPaths(subUrl, headers, paths, ownerRepo, commitId);
                }
            }
            String next = root.path("next").asText(null);
            if (next != null && !next.isBlank()) {
                collectBitbucketPaths(next, headers, paths, ownerRepo, commitId);
            }
        } catch (Exception e) {
            log.error("Bitbucket page fetch failed for {}: {}", url, e.getMessage());
        }
    }


    private String fetchFileContent(CodeRepository repo, String filePath, String commitId) {
        return switch (repo.getProvider()) {
            case GITHUB    -> fetchFromGitHub(repo, filePath, commitId);
            case GITLAB    -> fetchFromGitLab(repo, filePath, commitId);
            case BITBUCKET -> fetchFromBitbucket(repo, filePath, commitId);
        };
    }

    private String fetchFromGitHub(CodeRepository repo, String filePath, String commitId) {
        try {
            String ownerRepo = extractOwnerRepo(repo.getRepoUrl(), "github.com");
            String apiUrl = String.format(
                    "https://api.github.com/repos/%s/contents/%s?ref=%s",
                    ownerRepo, filePath, commitId
            );
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(buildGitHubHeaders(repo)), String.class
            );
            JsonNode json = objectMapper.readTree(response.getBody());
            String encoded = json.path("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(encoded));
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("File not found on GitHub: {}", filePath);
            return null;
        } catch (Exception e) {
            log.error("GitHub fetch error for {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private String fetchFromGitLab(CodeRepository repo, String filePath, String commitId) {
        try {
            String projectPath = extractOwnerRepo(repo.getRepoUrl(), "gitlab.com");
            String encodedProject = projectPath.replace("/", "%2F");
            String encodedFile = filePath.replace("/", "%2F");
            String apiUrl = String.format(
                    "https://gitlab.com/api/v4/projects/%s/repository/files/%s/raw?ref=%s",
                    encodedProject, encodedFile, commitId
            );
            HttpHeaders headers = new HttpHeaders();
            String token = decryptToken(repo);
            if (token != null) {
                headers.set("PRIVATE-TOKEN", token);
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("File not found on GitLab: {}", filePath);
            return null;
        } catch (Exception e) {
            log.error("GitLab fetch error for {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private String fetchFromBitbucket(CodeRepository repo, String filePath, String commitId) {
        try {
            String ownerRepo = extractOwnerRepo(repo.getRepoUrl(), "bitbucket.org");
            String apiUrl = String.format(
                    "https://api.bitbucket.org/2.0/repositories/%s/src/%s/%s",
                    ownerRepo, commitId, filePath
            );
            HttpHeaders headers = new HttpHeaders();
            String token = decryptToken(repo);
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
            }
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("File not found on Bitbucket: {}", filePath);
            return null;
        } catch (Exception e) {
            log.error("Bitbucket fetch error for {}: {}", filePath, e.getMessage());
            return null;
        }
    }


    private HttpHeaders buildGitHubHeaders(CodeRepository repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "CodeReviewSystem");
        String token = decryptToken(repo);
        if (token != null) {
            headers.set("Authorization", "Bearer " + token);
        }
        return headers;
    }

    /**
     * Decrypts the stored access token. Returns null if no token is set.
     */
    private String decryptToken(CodeRepository repo) {
        String token = repo.getAccessToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return encryptionService.decrypt(token);
    }

    private String extractOwnerRepo(String repoUrl, String domain) {
        int idx = repoUrl.indexOf(domain);
        if (idx == -1) throw new IllegalArgumentException("Domain not found in URL: " + repoUrl);
        String path = repoUrl.substring(idx + domain.length());
        path = path.startsWith("/") ? path.substring(1) : path;
        path = path.endsWith(".git") ? path.substring(0, path.length() - 4) : path;
        return path;
    }

    private String extractRepoName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        if (parts.length >= 2)
            return parts[parts.length - 2] + "_" + parts[parts.length - 1];
        return "unknown_repo";
    }
}