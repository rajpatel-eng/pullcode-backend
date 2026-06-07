package com.capstoneproject.codereviewsystem.controllers;

import com.capstoneproject.codereviewsystem.dtos.AuditLogResponse;
import com.capstoneproject.codereviewsystem.entity.AuditLog;
import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;
import com.capstoneproject.codereviewsystem.repos.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 30, sort = "createdAt") @NonNull Pageable pageable) {

        Page<AuditLog> logs;

        if (entityType != null && entityId != null) {
            logs = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        } else if (actorId != null) {
            logs = auditLogRepository.findByActorId(actorId, pageable);
        } else if (action != null) {
            logs = auditLogRepository.findByAction(action, pageable);
        } else if (from != null && to != null) {
            logs = auditLogRepository.findByCreatedAtBetween(from, to, pageable);
        } else {
            logs = auditLogRepository.findAll(pageable);
        }

        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    @GetMapping("/iam/{iamId}")
    public ResponseEntity<Page<AuditLogResponse>> getIamActivity(
            @PathVariable Long iamId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<AuditLog> logs = auditLogRepository
                .findByEntityIdAndEntityTypeOrderByCreatedAtDesc(iamId, "IAM_USER", pageable);
        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .actorId(log.getActorId())
                .actorEmail(log.getActorEmail())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .oldValue(log.getOldValue())
                .newValue(log.getNewValue())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}