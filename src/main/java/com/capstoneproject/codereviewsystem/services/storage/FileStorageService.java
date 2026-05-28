package com.capstoneproject.codereviewsystem.services.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;


@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProvider storageProvider;

    /** Relative path template: <source>/<projectId>/<submissionId>/<filePath> */
    private String buildPath(String source, String projectId, String submissionId, String filePath) {
        return String.join("/", source, projectId, submissionId, filePath);
    }

    private String buildSubmissionPath(String source, String projectId, String submissionId) {
        return String.join("/", source, projectId, submissionId);
    }

    public void saveFile(String source, String projectId,
                         String submissionId, String filePath, String content) {
        String path = buildPath(source, projectId, submissionId, filePath);
        try {
            storageProvider.saveText(path, content);
        } catch (IOException e) {
            log.error("Failed to save file: {} — {}", path, e.getMessage());
        }
    }

    public boolean submissionExists(String source, String projectId, String submissionId) {
        String path = buildSubmissionPath(source, projectId, submissionId);
        try {
            return storageProvider.exists(path);
        } catch (IOException e) {
            log.error("Failed to check submission existence: {}", path);
            return false;
        }
    }

    public void deleteSubmission(String source, String projectId, String submissionId) {
        String path = buildSubmissionPath(source, projectId, submissionId);
        try {
            storageProvider.deleteDirectory(path);
            log.info("Deleted submission: {}", path);
        } catch (IOException e) {
            log.error("Failed to delete submission: {}", e.getMessage());
        }
    }
    public String getSubmissionPath(String source, String projectId, String submissionId) {
        return buildSubmissionPath(source, projectId, submissionId);
    }
}