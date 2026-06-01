package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.CliDtos.*;
import com.capstoneproject.codereviewsystem.dtos.ProjectCommitDtos.*;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService;
import com.capstoneproject.codereviewsystem.services.project.ProjectCommitService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cli")
@RequiredArgsConstructor
public class CliController {

    private final CliTokenService cliTokenService;
    private final ProjectCommitService projectCommitService;


    @PostMapping("/projects/{projectId}/tokens")
    public ResponseEntity<CliTokenResponse> generateToken(
            @PathVariable Long projectId,
            @RequestBody GenerateTokenRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(201)
                .body(cliTokenService.generateToken(projectId, currentUser.getId(), req));
    }

    @GetMapping("/projects/{projectId}/tokens")
    public ResponseEntity<List<CliTokenResponse>> getProjectTokens(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(cliTokenService.getProjectTokens(projectId, currentUser.getId()));
    }

    @PatchMapping("/projects/{projectId}/tokens/{tokenId}/rename")
    public ResponseEntity<CliTokenResponse> renameToken(
            @PathVariable Long projectId,
            @PathVariable Long tokenId,
            @RequestBody RenameTokenRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
                cliTokenService.renameToken(projectId, tokenId, req, currentUser.getId()));
    }

    @PatchMapping("/projects/{projectId}/tokens/{tokenId}/toggle")
    public ResponseEntity<CliTokenResponse> toggleToken(
            @PathVariable Long projectId,
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
                cliTokenService.toggleTokenStatus(projectId, tokenId, currentUser.getId()));
    }

    @DeleteMapping("/projects/{projectId}/tokens/{tokenId}")
    public ResponseEntity<Void> deleteToken(
            @PathVariable Long projectId,
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        cliTokenService.deleteToken(projectId, tokenId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }


    @PostMapping(value = "/push", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommitResponse> push(
            @RequestHeader("X-CLI-Token") String cliToken,
            @RequestParam("file") MultipartFile file,
            @RequestParam("commitMessage") String commitMessage) {

        log.info("CLI push: file={} size={}", file.getOriginalFilename(), file.getSize());
        return ResponseEntity.status(201)
                .body(projectCommitService.pushFromCli(cliToken, file, commitMessage));
    }


    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<Page<CommitHistoryItem>> getCommitHistory(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(
                projectCommitService.getHistory(projectId, currentUser.getId(), page, size));
    }
}