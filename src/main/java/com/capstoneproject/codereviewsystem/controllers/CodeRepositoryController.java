package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryRequest;
import com.capstoneproject.codereviewsystem.dtos.CodeRepositoryResponse;
import com.capstoneproject.codereviewsystem.dtos.CommitHistoryResponse;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.repo.CodeRepositoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class CodeRepositoryController {

    private final CodeRepositoryService repoService;

    @PostMapping
    public ResponseEntity<CodeRepositoryResponse> addRepository(
            @Valid @RequestBody CodeRepositoryRequest request,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.status(201)
                .body(repoService.addRepository(request, currentUser.getId()));
    }

    @GetMapping
    public ResponseEntity<List<CodeRepositoryResponse>> getMyRepositories(
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(repoService.getMyRepositories(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CodeRepositoryResponse> getRepository(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(repoService.getRepository(id, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRepository(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser) {
        repoService.deleteRepository(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/token")
    public ResponseEntity<CodeRepositoryResponse> updateAccessToken(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @CurrentUser UserPrincipal currentUser) {
        String accessToken = body.getOrDefault("accessToken", "");
        return ResponseEntity.ok(
                repoService.updateAccessToken(id, accessToken, currentUser.getId()));
    }

    @GetMapping("/{id}/commits")
    public ResponseEntity<Page<CommitHistoryResponse>> getCommitHistory(
            @PathVariable Long id,
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(size = 20, sort = "committedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                repoService.getCommitHistory(id, currentUser.getId(), pageable));
    }

    @GetMapping("/commits")
    public ResponseEntity<Page<CommitHistoryResponse>> getAllMyCommits(
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(size = 20, sort = "committedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                repoService.getAllMyCommits(currentUser.getId(), pageable));
    }

    @GetMapping("/{id}/commits/branch/{branch}")
    public ResponseEntity<Page<CommitHistoryResponse>> getCommitsByBranch(
            @PathVariable Long id,
            @PathVariable String branch,
            @CurrentUser UserPrincipal currentUser,
            @PageableDefault(size = 20, sort = "committedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                repoService.getCommitsByBranch(id, branch, currentUser.getId(), pageable));
    }
}