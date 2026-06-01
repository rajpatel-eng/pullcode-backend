package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.ProjectCommitDtos.*;
import com.capstoneproject.codereviewsystem.dtos.ZipProjectDtos.*;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.project.ProjectCommitService;
import com.capstoneproject.codereviewsystem.services.zip.ZipProjectService;
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
@RequestMapping("/api/zip")
@RequiredArgsConstructor
public class ZipProjectController {

    private final ZipProjectService zipProjectService;
    private final ProjectCommitService projectCommitService;


    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(
            @RequestBody CreateProjectRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(201)
                .body(zipProjectService.createProject(req, currentUser.getId()));
    }

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponse>> getAllProjects(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(zipProjectService.getAllProjects(currentUser.getId()));
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(zipProjectService.getProject(projectId, currentUser.getId()));
    }

    @PatchMapping("/projects/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long projectId,
            @RequestBody UpdateProjectRequest req,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
                zipProjectService.updateProject(projectId, req, currentUser.getId()));
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        zipProjectService.deleteProject(projectId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }


    @PostMapping(value = "/projects/{projectId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommitResponse> uploadZip(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("tokenId") Long tokenId,
            @RequestParam("commitMessage") String commitMessage,
            @RequestParam(value = "extraMessage", required = false) String extraMessage,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("ZIP UI upload: project={} user={}", projectId, currentUser.getId());

        ZipUploadRequest req = new ZipUploadRequest();
        req.setTokenId(tokenId);
        req.setCommitMessage(commitMessage);
        req.setExtraMessage(extraMessage);

        return ResponseEntity.status(201)
                .body(projectCommitService.uploadFromUi(projectId, file, req, currentUser.getId()));
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