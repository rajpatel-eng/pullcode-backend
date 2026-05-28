package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.UserRequest;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getProfile(currentUser.getId()));
    }

    @PatchMapping("/profile/name")
    public ResponseEntity<?> updateName(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody UserRequest.UpdateName req) {
        return ResponseEntity.ok(userService.updateName(currentUser.getId(), req));
    }

    @PatchMapping("/profile/photo")
    public ResponseEntity<?> updatePhoto(
            @CurrentUser UserPrincipal currentUser,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userService.updateProfilePhoto(currentUser.getId(), file));
    }

    @PatchMapping("/profile/password")
    public ResponseEntity<?> changePassword(
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody UserRequest.ChangePassword req) {
        userService.changePassword(currentUser.getId(), req);
        return ResponseEntity.ok("Password changed successfully.");
    }
}
