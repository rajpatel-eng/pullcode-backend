package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.IamDtos;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.admin.IamManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/iam")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class IamManagementController {

    private final IamManagementService iamService;

    @PostMapping
    public ResponseEntity<IamDtos.Response> createIam(
            @Valid @RequestBody IamDtos.CreateRequest req,
            @CurrentUser UserPrincipal admin) {
        return ResponseEntity.status(201).body(iamService.createIam(req, admin));
    }

    @GetMapping
    public ResponseEntity<Page<IamDtos.Response>> listIam(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(iamService.listIam(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IamDtos.Response> getIam(@PathVariable Long id) {
        return ResponseEntity.ok(iamService.getIam(id));
    }

    @PatchMapping("/{id}/name")
    public ResponseEntity<IamDtos.Response> updateName(
            @PathVariable Long id,
            @Valid @RequestBody IamDtos.UpdateNameRequest req,
            @CurrentUser UserPrincipal admin) {
        return ResponseEntity.ok(iamService.updateName(id, req, admin));
    }

    @PatchMapping("/{id}/email")
    public ResponseEntity<IamDtos.Response> updateEmail(
            @PathVariable Long id,
            @Valid @RequestBody IamDtos.UpdateEmailRequest req,
            @CurrentUser UserPrincipal admin) {
        return ResponseEntity.ok(iamService.updateEmail(id, req, admin));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody IamDtos.ResetPasswordRequest req,
            @CurrentUser UserPrincipal admin) {
        iamService.resetPassword(id, req, admin);
        return ResponseEntity.ok("Password reset successfully.");
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<IamDtos.Response> pauseIam(
            @PathVariable Long id,
            @CurrentUser UserPrincipal admin) {
        return ResponseEntity.ok(iamService.pauseIam(id, admin));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<IamDtos.Response> resumeIam(
            @PathVariable Long id,
            @CurrentUser UserPrincipal admin) {
        return ResponseEntity.ok(iamService.resumeIam(id, admin));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteIam(
            @PathVariable Long id,
            @CurrentUser UserPrincipal admin) {
        iamService.deleteIam(id, admin);
        return ResponseEntity.noContent().build();
    }
}