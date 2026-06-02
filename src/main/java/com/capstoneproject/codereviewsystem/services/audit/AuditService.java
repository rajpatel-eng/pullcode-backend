package com.capstoneproject.codereviewsystem.services.audit;

import com.capstoneproject.codereviewsystem.entity.AuditLog;
import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;
import com.capstoneproject.codereviewsystem.repos.AuditLogRepository;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "encryptedApiKey", "apiKey", "newApiKey", "password",
            "newPassword", "accessToken", "webhookSecret"
    );

    public void log(AuditAction action,
                    UserPrincipal actor,
                    String entityType,
                    Long entityId,
                    Object oldValue,
                    Object newValue) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .actorId(actor.getId())
                    .actorEmail(actor.getEmail())
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(toMaskedJson(oldValue))
                    .newValue(toMaskedJson(newValue))
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action={} entity={}/{}: {}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    public void logCreate(AuditAction action, UserPrincipal actor,
                          String entityType, Long entityId, Object newValue) {
        log(action, actor, entityType, entityId, null, newValue);
    }

    public void logDelete(AuditAction action, UserPrincipal actor,
                          String entityType, Long entityId, Object oldValue) {
        log(action, actor, entityType, entityId, oldValue, null);
    }

    private String toMaskedJson(Object value) {
        if (value == null) return null;
        try {
            Map<String, Object> map = objectMapper.convertValue(value, new TypeReference<>() {});
            // Replace sensitive keys with redaction marker
            SENSITIVE_FIELDS.forEach(field -> {
                if (map.containsKey(field)) {
                    map.put(field, "[REDACTED]");
                }
            });
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Could not serialize audit value: {}", e.getMessage());
            return "{\"_error\":\"serialization_failed\"}";
        }
    }
}