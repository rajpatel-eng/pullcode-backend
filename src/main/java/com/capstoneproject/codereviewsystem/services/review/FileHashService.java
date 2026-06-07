package com.capstoneproject.codereviewsystem.services.review;

import com.capstoneproject.codereviewsystem.entity.FileReview;
import com.capstoneproject.codereviewsystem.repos.FileReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class FileHashService {

    private final FileReviewRepository fileReviewRepo;

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;


    public DiffResult diff(String storagePath, Long repoId, Long projectId) {
        Path root = resolveRoot(storagePath);

        List<String> allFiles = listFiles(root);
        Map<String, String> currentHashes = computeHashes(root, allFiles);

        List<String> newFiles       = new ArrayList<>();
        List<String> modifiedFiles  = new ArrayList<>();
        List<String> unchangedFiles = new ArrayList<>();
        Map<String, String> hashSnapshot = new LinkedHashMap<>();

        for (String filePath : allFiles) {
            String currentHash = currentHashes.getOrDefault(filePath, "");
            hashSnapshot.put(filePath, currentHash);

            Optional<FileReview> prev = repoId != null
                    ? fileReviewRepo.findTopByRepoAndFilePathOrderByCreatedAtDesc(repoId, filePath)
                    : fileReviewRepo.findTopByProjectAndFilePathOrderByCreatedAtDesc(projectId, filePath);

            if (prev.isEmpty()) {
                newFiles.add(filePath);
            } else {
                String prevHash = prev.get().getFileHash();
                if (prevHash == null || !prevHash.equals(currentHash)) {
                    modifiedFiles.add(filePath);
                } else {
                    unchangedFiles.add(filePath);
                }
            }
        }

        log.info("DiffResult: new={} modified={} unchanged={} total={}",
                newFiles.size(), modifiedFiles.size(), unchangedFiles.size(), allFiles.size());

        return new DiffResult(newFiles, modifiedFiles, unchangedFiles, hashSnapshot);
    }


    public String hashFile(String storagePath, String relativePath) {
        Path file = resolveRoot(storagePath).resolve(relativePath);
        return computeHash(file);
    }


    private List<String> listFiles(Path root) {
        if (!Files.exists(root)) return List.of();
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Could not list files under {}: {}", root, e.getMessage());
            return List.of();
        }
    }


    private Map<String, String> computeHashes(Path root, List<String> files) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String rel : files) {
            Path abs = root.resolve(rel);
            result.put(rel, computeHash(abs));
        }
        return result;
    }

    private String computeHash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) digest.update(buf, 0, n);
            }
            return hexEncode(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            log.warn("Could not hash file {}: {}", file, e.getMessage());
            return "";
        }
    }

    private String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private Path resolveRoot(String storagePath) {
        return Paths.get(basePath, storagePath.replace("/", File.separator));
    }

    public record DiffResult(
            List<String> newFiles,
            List<String> modifiedFiles,
            List<String> unchangedFiles,
            Map<String, String> hashSnapshot
    ) {
        public List<String> changedFiles() {
            List<String> changed = new ArrayList<>(newFiles);
            changed.addAll(modifiedFiles);
            return changed;
        }

        public boolean hasChanges() {
            return !newFiles.isEmpty() || !modifiedFiles.isEmpty();
        }

        public int totalFiles() {
            return newFiles.size() + modifiedFiles.size() + unchangedFiles.size();
        }
    }
}
