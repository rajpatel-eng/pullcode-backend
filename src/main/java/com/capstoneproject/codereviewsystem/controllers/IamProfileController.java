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
@RequestMapping("/api/iam/profile")
@PreAuthorize("hasRole('IAM')")
@RequiredArgsConstructor
public class IamProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal iam) {
        return ResponseEntity.ok(userService.getProfile(iam.getId()));
    }

    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(
            @CurrentUser UserPrincipal iam,
            @Valid @RequestBody UserRequest.ChangePassword req) {
        userService.changePassword(iam.getId(), req);
        return ResponseEntity.ok("Password changed successfully.");
    }

    @PatchMapping("/photo")
    public ResponseEntity<?> updatePhoto(
            @CurrentUser UserPrincipal iam,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfilePhoto(iam.getId(), file));
    }
}