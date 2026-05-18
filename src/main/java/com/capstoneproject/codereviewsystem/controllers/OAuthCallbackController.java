package com.capstoneproject.codereviewsystem.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

// AuthController ke bahar ek alag controller banao
@RestController
@RequiredArgsConstructor
public class OAuthCallbackController {

    @GetMapping("/login-success")
    public ResponseEntity<?> loginSuccess(
            @RequestParam(required = false) String token,
            @RequestParam(required = false) String refreshToken) {

        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "No token found."
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "OAuth2 login successful!",
            "accessToken", token,
            "refreshToken", refreshToken != null ? refreshToken : ""
        ));
    }

    @GetMapping("/api/auth/oauth2-error")
    public ResponseEntity<?> oauth2Error(
            @RequestParam(defaultValue = "unknown") String error) {
        return ResponseEntity.badRequest().body(Map.of(
            "status", "error",
            "message", "OAuth2 login failed: " + error
        ));
    }
}
