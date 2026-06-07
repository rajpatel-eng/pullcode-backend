package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.security.CurrentUser;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/review-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CurrentUser UserPrincipal user) {
        return registry.register(user.getId());
    }
}
