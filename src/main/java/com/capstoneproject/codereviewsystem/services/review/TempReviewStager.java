package com.capstoneproject.codereviewsystem.services.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TempReviewStager {

    @Value("${app.storage.local.base-path:uploads}")
    private String basePath;

    private static final String TEMP_DIR = "temp";

    public String stage(String storagePath,
                        String commitId,
                        Collection<String> changedFiles,
                        Collection<String> importedFiles) throws IOException {

        Path source = resolveRoot(storagePath);
        String tempRelPath = TEMP_DIR + "/" + commitId;
        Path tempDir = resolveRoot(tempRelPath);

        deleteDirectory(tempDir);
        Files.createDirectories(tempDir);

        int copied = 0;
        for (String rel : changedFiles) {
            copied += copyFile(source, tempDir, rel);
        }
        for (String rel : importedFiles) {
            copied += copyFile(source, tempDir, rel);
        }

        log.info("Staged {} files to temp/{} (changed={} imports={})",
                copied, commitId, changedFiles.size(), importedFiles.size());

        return tempRelPath;
    }

    public List<String> listStagedFiles(String tempStagingPath) {
        Path tempDir = resolveRoot(tempStagingPath);
        if (!Files.exists(tempDir)) {
            log.warn("Temp directory does not exist: {}", tempDir);
            return List.of();
        }
        try (var stream = Files.walk(tempDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> tempDir.relativize(p).toString()
                            .replace(File.separatorChar, '/'))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Could not list staged files in {}: {}", tempDir, e.getMessage());
            return List.of();
        }
    }

    public void cleanup(String tempStagingPath) {
        if (tempStagingPath == null || tempStagingPath.isBlank()) return;
        Path tempDir = resolveRoot(tempStagingPath);
        try {
            deleteDirectory(tempDir);
            log.debug("Cleaned up temp dir: {}", tempStagingPath);
        } catch (IOException e) {
            log.warn("Could not clean up temp dir {}: {}", tempStagingPath, e.getMessage());
        }
    }

    private int copyFile(Path source, Path dest, String relative) {
        Path src  = source.resolve(relative.replace('/', File.separatorChar));
        Path tgt  = dest.resolve(relative.replace('/', File.separatorChar));
        if (!Files.exists(src)) {
            log.warn("Source file not found, skipping: {}", src);
            return 0;
        }
        try {
            Files.createDirectories(tgt.getParent());
            Files.copy(src, tgt, StandardCopyOption.REPLACE_EXISTING);
            return 1;
        } catch (IOException e) {
            log.warn("Could not copy {} → {}: {}", src, tgt, e.getMessage());
            return 0;
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try { Files.delete(p); }
                      catch (IOException e) {
                          log.warn("Could not delete {}: {}", p, e.getMessage());
                      }
                  });
        }
    }

    private Path resolveRoot(String relativePath) {
        return Paths.get(basePath, relativePath.replace("/", File.separator));
    }
}
