package com.capstoneproject.codereviewsystem.controllers;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstoneproject.codereviewsystem.dtos.AuthRequest;
import com.capstoneproject.codereviewsystem.dtos.OtpResponse;
import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.auth.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    public ResponseEntity<OtpResponse> sendOtp(
            @Valid @RequestBody AuthRequest.SendOtp req) {
        return ResponseEntity.ok(authService.sendOtp(req.getEmail()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest.Register req) {
        return ResponseEntity.status(201).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest.Login req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody AuthRequest.RefreshTokenRequest req) {
        return ResponseEntity.ok(authService.refreshToken(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CurrentUser UserPrincipal user) {
        authService.logout(user.getId());
        return ResponseEntity.ok("Logged out");
    }

}
