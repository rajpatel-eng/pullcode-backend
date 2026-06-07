package com.capstoneproject.codereviewsystem.services.zip;

import com.capstoneproject.codereviewsystem.dtos.ExtractResult;
import com.capstoneproject.codereviewsystem.services.storage.StorageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Slf4j
@Service
@RequiredArgsConstructor
public class ZipStorageService {

    private final StorageProvider storageProvider;

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
        ".html", ".css", ".scss", ".xml", ".json",
        ".yml", ".yaml", ".properties", ".md", ".txt",
        ".sql", ".sh", ".bat", ".gradle", ".pom",
        ".kt", ".go", ".rb", ".php", ".cs", ".cpp",
        ".c", ".h", ".rs", ".swift", ".dart"
    );

    private static final long MAX_ZIP_SIZE = 50 * 1024 * 1024; // 50 MB


    private String submissionPath(Long userId, Long projectId, String timestamp) {
        return "zip/user_" + userId + "/project_" + projectId + "/" + timestamp;
    }

    private String latestPath(Long userId, Long projectId) {
        return "zip/user_" + userId + "/project_" + projectId + "/latest";
    }

    private String projectPath(Long userId, Long projectId) {
        return "zip/user_" + userId + "/project_" + projectId;
    }


    public ExtractResult extractAndStore(MultipartFile zipFile, Long userId, Long projectId)
            throws IOException {

        if (zipFile.isEmpty()) throw new IllegalArgumentException("ZIP file is empty");
        if (zipFile.getSize() > MAX_ZIP_SIZE) throw new IllegalArgumentException("ZIP file too large (max 50MB)");

        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are allowed");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String storagePath = submissionPath(userId, projectId, timestamp);

        List<String> extractedFiles = new ArrayList<>();
        int skippedFiles = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entry.isDirectory() || shouldSkip(entryName) || !hasAllowedExtension(entryName)) {
                    skippedFiles++;
                    zis.closeEntry();
                    continue;
                }

                if (entryName.contains("..")) {
                    log.warn("ZIP slip attack detected: {}", entryName);
                    zis.closeEntry();
                    continue;
                }

                String filePath = storagePath + "/" + entryName;
                byte[] bytes = zis.readAllBytes();
                storageProvider.saveBytes(filePath, bytes);
                extractedFiles.add(entryName);
                zis.closeEntry();
            }
        }

        log.info("ZIP extracted: {} files saved, {} skipped | path: {}",
                extractedFiles.size(), skippedFiles, storagePath);

        return ExtractResult.builder()
                .storagePath(storagePath)
                .extractedFiles(extractedFiles)
                .totalFiles(extractedFiles.size())
                .skippedFiles(skippedFiles)
                .originalFileName(originalName)
                .fileSizeBytes(zipFile.getSize())
                .build();
    }

    public void updateLatestFolder(Long userId, Long projectId, String newStoragePath)
            throws IOException {
        String latestPath = latestPath(userId, projectId);
        storageProvider.deleteDirectory(latestPath);
        storageProvider.copyDirectory(newStoragePath, latestPath);


        deleteOldSubmissions(userId, projectId, newStoragePath);

        log.info("Latest folder updated for project: {}", projectId);
    }


    private void deleteOldSubmissions(Long userId, Long projectId, String currentSubmissionPath) {
        try {
            storageProvider.deleteDirectory(currentSubmissionPath);
            log.info("Deleted current submission folder after copying to latest: {}", currentSubmissionPath);
        } catch (IOException e) {
            log.warn("Could not delete current submission folder {}: {}", currentSubmissionPath, e.getMessage());
        }
    }

    public void deleteUploadFolder(String storagePath) {
        try {
            storageProvider.deleteDirectory(storagePath);
            log.info("Deleted upload folder: {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to delete folder: {}", storagePath);
        }
    }

    public void deleteProjectFolder(Long userId, Long projectId) {
        try {
            storageProvider.deleteDirectory(projectPath(userId, projectId));
            log.info("Deleted project folder: user_{}/project_{}", userId, projectId);
        } catch (IOException e) {
            log.error("Failed to delete project folder: {}", e.getMessage());
        }
    }


    private boolean shouldSkip(String name) {
        String lower = name.toLowerCase();
        return lower.contains("/.git/") || lower.startsWith(".git/")
            || lower.contains("/node_modules/") || lower.contains("/.idea/")
            || lower.contains("/target/") || lower.contains("/build/")
            || lower.contains("/__pycache__/") || lower.contains("/.gradle/")
            || lower.startsWith("__macosx/")
            || lower.endsWith(".class") || lower.endsWith(".jar")
            || lower.endsWith(".war") || lower.endsWith(".exe")
            || lower.endsWith(".dll");
    }

    private boolean hasAllowedExtension(String filename) {
        String lower = filename.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
}