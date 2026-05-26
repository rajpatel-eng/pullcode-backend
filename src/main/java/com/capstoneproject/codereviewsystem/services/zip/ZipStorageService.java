package com.capstoneproject.codereviewsystem.services.zip;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.capstoneproject.codereviewsystem.dtos.ExtractResult;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
public class ZipStorageService {

    @Value("${app.storage.base-path:uploads}")
    private String basePath;

    // Allowed file extensions — security ke liye
    private static final List<String> ALLOWED_EXTENSIONS = List.of(
        ".java", ".py", ".js", ".ts", ".jsx", ".tsx",
        ".html", ".css", ".scss", ".xml", ".json",
        ".yml", ".yaml", ".properties", ".md", ".txt",
        ".sql", ".sh", ".bat", ".gradle", ".pom",
        ".kt", ".go", ".rb", ".php", ".cs", ".cpp",
        ".c", ".h", ".rs", ".swift", ".dart"
    );

    private static final long MAX_ZIP_SIZE = 50 * 1024 * 1024;


    public ExtractResult extractAndStore(MultipartFile zipFile, Long userId, Long projectId)
            throws IOException {

        if (zipFile.isEmpty()) throw new IllegalArgumentException("ZIP file is empty");
        if (zipFile.getSize() > MAX_ZIP_SIZE) throw new IllegalArgumentException("ZIP file too large (max 50MB)");

        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are allowed");
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        Path storageFolder = Paths.get(basePath, "zip",
                "user_" + userId,
                "project_" + projectId,
                timestamp);

        Files.createDirectories(storageFolder);

        List<String> extractedFiles = new ArrayList<>();
        int skippedFiles = 0;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if (shouldSkip(entryName)) {
                    skippedFiles++;
                    zis.closeEntry();
                    continue;
                }

                if (!hasAllowedExtension(entryName)) {
                    skippedFiles++;
                    zis.closeEntry();
                    continue;
                }

                Path targetFile = storageFolder.resolve(entryName).normalize();
                if (!targetFile.startsWith(storageFolder)) {
                    log.warn("ZIP slip attack detected: {}", entryName);
                    zis.closeEntry();
                    continue;
                }

                Files.createDirectories(targetFile.getParent());

                try (OutputStream os = Files.newOutputStream(targetFile,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    zis.transferTo(os);
                }

                extractedFiles.add(entryName);
                zis.closeEntry();
            }
        }

        log.info("ZIP extracted: {} files saved, {} skipped | path: {}",
                extractedFiles.size(), skippedFiles, storageFolder);

        return ExtractResult.builder()
                .storagePath(storageFolder.toString())
                .extractedFiles(extractedFiles)
                .totalFiles(extractedFiles.size())
                .skippedFiles(skippedFiles)
                .originalFileName(originalName)
                .fileSizeBytes(zipFile.getSize())
                .build();
    }


    public void updateLatestFolder(Long userId, Long projectId, String newStoragePath)
            throws IOException {
        Path latestLink = Paths.get(basePath, "zip",
                "user_" + userId, "project_" + projectId, "latest");

        if (Files.exists(latestLink)) {
            deleteDirectory(latestLink);
        }

        Path source = Paths.get(newStoragePath);
        copyDirectory(source, latestLink);

        log.info("Latest folder updated for project: {}", projectId);
    }

    public void deleteUploadFolder(String storagePath) {
        try {
            Path folder = Paths.get(storagePath);
            deleteDirectory(folder);
            log.info("Deleted upload folder: {}", storagePath);
        } catch (IOException e) {
            log.error("Failed to delete folder: {}", storagePath);
        }
    }


    public void deleteProjectFolder(Long userId, Long projectId) {
        try {
            Path folder = Paths.get(basePath, "zip",
                    "user_" + userId, "project_" + projectId);
            deleteDirectory(folder);
            log.info("Deleted project folder: user_{}/project_{}", userId, projectId);
        } catch (IOException e) {
            log.error("Failed to delete project folder: {}", e.getMessage());
        }
    }


    private boolean shouldSkip(String name) {
        String lower = name.toLowerCase();
        return lower.contains("/.git/")
                || lower.startsWith(".git/")
                || lower.contains("/node_modules/")
                || lower.contains("/.idea/")
                || lower.contains("/target/")
                || lower.contains("/build/")
                || lower.contains("/__pycache__/")
                || lower.contains("/.gradle/")
                || lower.startsWith("__macosx/")
                || lower.endsWith(".class")
                || lower.endsWith(".jar")
                || lower.endsWith(".war")
                || lower.endsWith(".exe")
                || lower.endsWith(".dll");
    }

    private boolean hasAllowedExtension(String filename) {
        String lower = filename.toLowerCase();
        return ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.delete(p); }
                        catch (IOException e) { log.warn("Could not delete: {}", p); }
                    });
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("Copy error: {}", e.getMessage());
            }
        });
    }

}
