package com.capstoneproject.codereviewsystem.dtos;

import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private AuditAction action;
    private Long actorId;
    private String actorEmail;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private String ipAddress;
    private LocalDateTime createdAt;
}