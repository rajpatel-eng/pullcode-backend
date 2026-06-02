package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.AdminAuthDtos;
import com.capstoneproject.codereviewsystem.services.admin.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminAuthDtos.LoginRequest req) {
        return ResponseEntity.ok(adminAuthService.login(req));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody AdminAuthDtos.OtpVerifyRequest req) {
        return ResponseEntity.ok(adminAuthService.verifyOtp(req));
    }
}