package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.UserRequest;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/profile")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal admin) {
        return ResponseEntity.ok(userService.getProfile(admin.getId()));
    }

    @PatchMapping("/name")
    public ResponseEntity<?> updateName(
            @CurrentUser UserPrincipal admin,
            @Valid @RequestBody UserRequest.UpdateName req) {
        return ResponseEntity.ok(userService.updateName(admin.getId(), req));
    }

    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(
            @CurrentUser UserPrincipal admin,
            @Valid @RequestBody UserRequest.ChangePassword req) {
        userService.changePassword(admin.getId(), req);
        return ResponseEntity.ok("Password changed successfully.");
    }

    @PatchMapping("/photo")
    public ResponseEntity<?> updatePhoto(
            @CurrentUser UserPrincipal admin,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfilePhoto(admin.getId(), file));
    }
}