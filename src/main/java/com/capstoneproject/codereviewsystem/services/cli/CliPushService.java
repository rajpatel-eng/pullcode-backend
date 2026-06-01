package com.capstoneproject.codereviewsystem.services.cli;

import com.capstoneproject.codereviewsystem.dtos.ProjectCommitDtos.CommitResponse;
import com.capstoneproject.codereviewsystem.services.project.ProjectCommitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin facade kept so the CLI controller has a named entry-point.
 * All real logic lives in ProjectCommitService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CliPushService {

    private final ProjectCommitService projectCommitService;

    public CommitResponse push(
            String rawToken,
            MultipartFile zipFile,
            String commitMessage) {

        return projectCommitService.pushFromCli(rawToken, zipFile, commitMessage);
    }
}