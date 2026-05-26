package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.ZipProjectDtos.*;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.zip.ZipProjectService;
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
@RequestMapping("/api/zip")
@RequiredArgsConstructor
public class ZipProjectController {

    private final ZipProjectService zipProjectService;


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

    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<ProjectWithHistoryResponse> getProjectWithHistory(
            @PathVariable Long projectId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
                zipProjectService.getProjectWithHistory(projectId, currentUser.getId()));
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


    @PostMapping(value = "/projects/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadZip(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("subtitle") String subtitle,
            @RequestParam(value = "commitMessage", required = false) String commitMessage,  // NEW
            @RequestParam(value = "extraMessage",  required = false) String extraMessage,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        log.info("ZIP upload request: project={} subtitle='{}' user={}",
                id, subtitle, currentUser.getId());

        UploadResponse response = zipProjectService.uploadZip(
                id, file, subtitle, commitMessage, extraMessage, currentUser.getId());

        return ResponseEntity.status(201).body(response);
    }


    @GetMapping("/projects/{projectId}/history/{historyId}")
    public ResponseEntity<UploadHistoryResponse> getUploadHistory(
            @PathVariable Long projectId,
            @PathVariable Long historyId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(
                zipProjectService.getUploadHistory(projectId, historyId, currentUser.getId()));
    }

    @DeleteMapping("/projects/{projectId}/history/{historyId}")
    public ResponseEntity<Void> deleteUploadHistory(
            @PathVariable Long projectId,
            @PathVariable Long historyId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        zipProjectService.deleteUploadHistory(projectId, historyId, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}