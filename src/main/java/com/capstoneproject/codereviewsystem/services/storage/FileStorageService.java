package com.capstoneproject.codereviewsystem.services.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Slf4j
@Service
public class FileStorageService {

    @Value("${app.storage.base-path:uploads}")
    private String basePath;

    public void saveFile(String source, String projectId,
                         String submissionId, String filePath, String content) {
        try {
            Path fullPath = Paths.get(basePath, source, projectId, submissionId, filePath);

            Files.createDirectories(fullPath.getParent());

            Files.writeString(fullPath, content, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.debug("File saved: {}", fullPath);

        } catch (IOException e) {
            log.error("Failed to save file: {}/{}/{}/{} — {}",
                    source, projectId, submissionId, filePath, e.getMessage());
        }
    }

    public Path getSubmissionFolder(String source, String projectId, String submissionId) {
        return Paths.get(basePath, source, projectId, submissionId);
    }

    public boolean submissionExists(String source, String projectId, String submissionId) {
        Path folder = getSubmissionFolder(source, projectId, submissionId);
        return Files.exists(folder) && folder.toFile().list() != null
                && folder.toFile().list().length > 0;
    }
    public void deleteSubmission(String source, String projectId, String submissionId) {
        try {
            Path folder = getSubmissionFolder(source, projectId, submissionId);
            deleteDirectory(folder);
            log.info("Deleted submission folder: {}", folder);
        } catch (IOException e) {
            log.error("Failed to delete submission: {}", e.getMessage());
        }
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
}