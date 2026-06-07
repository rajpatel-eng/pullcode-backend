package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.FileReviewDtos.CommitReviewResponse;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.review.FileReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class FileReviewController {

    private final FileReviewService fileReviewService;

    @GetMapping("/commit/{commitHistoryId}/errors")
    public ResponseEntity<CommitReviewResponse> getCommitErrors(
            @PathVariable Long commitHistoryId,
            @CurrentUser UserPrincipal user) {

        return ResponseEntity.ok(fileReviewService.getCommitHistoryReview(commitHistoryId,user.getId()));
    }

    @GetMapping("/project-commit/{projectCommitId}/errors")
    public ResponseEntity<CommitReviewResponse> getProjectCommitErrors(
            @PathVariable Long projectCommitId,
            @CurrentUser UserPrincipal user) {

        return ResponseEntity.ok(fileReviewService.getProjectCommitReview(projectCommitId,user.getId()));
    }
}
