package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.ProjectCommitDtos.*;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.project.ProjectCommitService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commits")
@RequiredArgsConstructor
public class UserCommitsController {

    private final ProjectCommitService projectCommitService;

    @GetMapping("/all")
    public ResponseEntity<Page<UserCommitItem>> getAllMyCommits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        return ResponseEntity.ok(
                projectCommitService.getUserCommits(currentUser.getId(), page, size));
    }
}
