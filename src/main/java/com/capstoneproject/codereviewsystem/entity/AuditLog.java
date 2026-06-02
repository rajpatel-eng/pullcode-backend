package com.capstoneproject.codereviewsystem.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;

@Entity
@Table(name = "audit_logs",
        indexes = {
            @Index(name = "idx_audit_actor",   columnList = "actorId"),
            @Index(name = "idx_audit_entity",  columnList = "entityType, entityId"),
            @Index(name = "idx_audit_created", columnList = "createdAt")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AuditAction action;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 255)
    private String actorEmail;

    @Column(nullable = false, length = 100)
    private String entityType;

    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}