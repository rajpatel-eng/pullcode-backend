package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.AiModelDtos;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.model.AiModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService modelService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> createModel(
            @Valid @RequestBody AiModelDtos.CreateRequest req,
            @CurrentUser UserPrincipal actor) {
        return ResponseEntity.status(201).body(modelService.createModel(req, actor));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<Page<AiModelDtos.Response>> listModels(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(modelService.listModels(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> getModel(@PathVariable Long id) {
        return ResponseEntity.ok(modelService.getModel(id));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','IAM','USER')")
    public ResponseEntity<List<AiModelDtos.SummaryResponse>> listActiveModels() {
        return ResponseEntity.ok(modelService.listActiveModels());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> updateModel(
            @PathVariable Long id,
            @Valid @RequestBody AiModelDtos.UpdateRequest req,
            @CurrentUser UserPrincipal actor) {
        return ResponseEntity.ok(modelService.updateModel(id, req, actor));
    }

    @PatchMapping("/{id}/api-key")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<?> rotateApiKey(
            @PathVariable Long id,
            @Valid @RequestBody AiModelDtos.ApiKeyUpdateRequest req,
            @CurrentUser UserPrincipal actor) {
        modelService.rotateApiKey(id, req.getNewApiKey(), actor);
        return ResponseEntity.ok(Map.of("message", "API key updated successfully"));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> pauseModel(
            @PathVariable Long id,
            @CurrentUser UserPrincipal actor) {
        return ResponseEntity.ok(modelService.pauseModel(id, actor));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> resumeModel(
            @PathVariable Long id,
            @CurrentUser UserPrincipal actor) {
        return ResponseEntity.ok(modelService.resumeModel(id, actor));
    }

    @PostMapping("/{id}/set-default")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<AiModelDtos.Response> setDefault(
            @PathVariable Long id,
            @CurrentUser UserPrincipal actor) {
        return ResponseEntity.ok(modelService.setDefault(id, actor));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','IAM')")
    public ResponseEntity<?> deleteModel(
            @PathVariable Long id,
            @CurrentUser UserPrincipal actor) {
        int migrated = modelService.deleteModel(id, actor);
        return ResponseEntity.accepted()
                .body(Map.of(
                        "affected", migrated,
                        "message", migrated + " repository/repositories migrated to the default model"
                ));
    }
}