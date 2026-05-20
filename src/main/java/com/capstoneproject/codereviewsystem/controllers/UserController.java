package com.capstoneproject.codereviewsystem.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@CurrentUser UserPrincipal currentUser) {
        return ResponseEntity.ok(Map.of(
            "id", currentUser.getId(),
            "name", currentUser.getName(),
            "email", currentUser.getEmail(),
            "roles", currentUser.getAuthorities()
        ));
    }
}