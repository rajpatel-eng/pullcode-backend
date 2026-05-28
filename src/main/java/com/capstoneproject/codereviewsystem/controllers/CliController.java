package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.CliDtos.*;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.cli.CliPushService;
import com.capstoneproject.codereviewsystem.services.cli.CliTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final CliPushService cliPushService;

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

    @DeleteMapping("/projects/{projectId}/tokens/{tokenId}")
    public ResponseEntity<Void> revokeToken(
            @PathVariable Long projectId,
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        cliTokenService.revokeToken(projectId, tokenId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/projects/{projectId}/tokens/{tokenId}/toggle")
    public ResponseEntity<CliTokenResponse> toggleToken(
            @PathVariable Long projectId,
            @PathVariable Long tokenId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(cliTokenService.toggleTokenStatus(projectId, tokenId, currentUser.getId()));
    }

    @PostMapping(value = "/push", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CliPushResponse> push(
            @RequestHeader("X-CLI-Token") String cliToken,
            @RequestParam("file") MultipartFile file,
            @RequestParam("commitMessage") String commitMessage,
            @RequestParam(value = "hostname", required = false) String hostname,
            @RequestParam(value = "osUser", required = false) String osUser) {

        log.info("CLI push: file={} size={}", file.getOriginalFilename(), file.getSize());
        return ResponseEntity.status(201)
                .body(cliPushService.push(cliToken, file, commitMessage, hostname, osUser));
    }

    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<ProjectCliHistoryResponse> getCliHistory(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(cliPushService.getCliHistory(projectId, currentUser.getId()));
    }
}