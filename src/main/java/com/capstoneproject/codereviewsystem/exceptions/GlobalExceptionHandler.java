package com.capstoneproject.codereviewsystem.exceptions;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 400 Bad Request ───────────────────────────────────────
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // ── 403 Forbidden ─────────────────────────────────────────
    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<Map<String, Object>> handleTokenRefresh(TokenRefreshException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // ── 401 Unauthorized (wrong password) ────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    // ── 400 Validation errors (@Valid fails) ──────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.put(e.getField(), e.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("status", 400);
        body.put("error", "Validation Failed");
        body.put("message", "Please fix the errors in the request");
        body.put("fields", fieldErrors);
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.badRequest().body(body);
    }

    // ── 500 Catch all ─────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {

        String correlationId = UUID.randomUUID().toString();

        log.error("Unhandled exception [{}]",correlationId,ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,"An unexpected error occurred. Reference: "+ correlationId);
    }

    // ── Helper ────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    // ── 401 Webhook auth failure ───────────────────────────────
    @ExceptionHandler(WebhookAuthException.class)
    public ResponseEntity<Map<String, Object>> handleWebhookAuth(
            WebhookAuthException ex) {
        // Do NOT echo ex.getMessage() — never tell callers why auth failed
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage());
    }
}