package com.capstoneproject.codereviewsystem.services.model;

import com.capstoneproject.codereviewsystem.dtos.AiModelDtos;
import com.capstoneproject.codereviewsystem.entity.AiModel;
import com.capstoneproject.codereviewsystem.entity.CodeRepository;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.AiModelRepository;
import com.capstoneproject.codereviewsystem.repos.CodeRepositoryRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.audit.AuditService;
import com.capstoneproject.codereviewsystem.services.encryption.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelService {

    private final AiModelRepository modelRepository;
    private final CodeRepositoryRepository repoRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;


    @Transactional
    public AiModelDtos.Response createModel(AiModelDtos.CreateRequest req, UserPrincipal actor) {
        User createdBy = userRepository.findById(actor.getId())
                .orElseThrow(() -> new BadRequestException("Actor not found"));

        AiModel model = AiModel.builder()
                .name(req.getName())
                .provider(req.getProvider())
                .encryptedApiKey(encryptionService.encrypt(req.getApiKey()))
                .apiBaseUrl(req.getApiBaseUrl())
                .systemPrompt(req.getSystemPrompt())
                .temperature(req.getTemperature())
                .maxTokens(req.getMaxTokens())
                .description(req.getDescription())
                .active(true)
                .defaultModel(false)
                .createdBy(createdBy)
                .build();

        if (!modelRepository.existsByDefaultModelTrueAndDeletedFalse()) {
            model.setDefaultModel(true);
        } else if (req.isDefaultModel()) {
            demoteCurrentDefault();
            model.setDefaultModel(true);
        }

        modelRepository.save(model);
        log.info("AI model created: {} by {}", model.getName(), actor.getEmail());
        auditService.logCreate(AuditAction.AI_MODEL_CREATED, actor, "AI_MODEL", model.getId(),
                toAuditSnapshot(model));

        return toResponse(model);
    }


    public Page<AiModelDtos.Response> listModels(Pageable pageable) {
        return modelRepository.findByDeletedFalse(pageable).map(this::toResponse);
    }

    public AiModelDtos.Response getModel(Long id) {
        AiModel model = findActiveModel(id);
        return toResponse(model);
    }

    public List<AiModelDtos.SummaryResponse> listActiveModels() {
        return modelRepository.findByActiveTrueAndDeletedFalse()
                .stream()
                .map(m -> AiModelDtos.SummaryResponse.builder()
                        .id(m.getId())
                        .name(m.getName())
                        .provider(m.getProvider())
                        .defaultModel(m.isDefaultModel())
                        .build())
                .toList();
    }


    @Transactional
    public AiModelDtos.Response updateModel(Long id, AiModelDtos.UpdateRequest req,
                                            UserPrincipal actor) {
        AiModel model = findActiveModel(id);
        Map<String, Object> oldSnapshot = toAuditSnapshot(model);

        model.setName(req.getName());
        model.setProvider(req.getProvider());
        model.setApiBaseUrl(req.getApiBaseUrl());
        model.setSystemPrompt(req.getSystemPrompt());
        model.setTemperature(req.getTemperature());
        model.setMaxTokens(req.getMaxTokens());
        model.setDescription(req.getDescription());
        modelRepository.save(model);

        auditService.log(AuditAction.AI_MODEL_UPDATED, actor, "AI_MODEL", id,
                oldSnapshot, toAuditSnapshot(model));
        return toResponse(model);
    }


    @Transactional
    public void rotateApiKey(Long id, String newRawKey, UserPrincipal actor) {
        AiModel model = findActiveModel(id);
        model.setEncryptedApiKey(encryptionService.encrypt(newRawKey));
        modelRepository.save(model);
        log.info("API key rotated for model {} by {}", id, actor.getEmail());
        auditService.log(AuditAction.AI_MODEL_API_KEY_ROTATED, actor, "AI_MODEL", id,
                Map.of("modelName", model.getName()),
                Map.of("modelName", model.getName(), "note", "API key replaced"));
    }


    @Transactional
    public AiModelDtos.Response pauseModel(Long id, UserPrincipal actor) {
        AiModel model = findActiveModel(id);
        if (model.isDefaultModel()) {
            throw new BadRequestException("Cannot pause the default model. Set another model as default first.");
        }
        Map<String, Object> old = toAuditSnapshot(model);
        model.setActive(false);
        modelRepository.save(model);
        auditService.log(AuditAction.AI_MODEL_PAUSED, actor, "AI_MODEL", id, old, toAuditSnapshot(model));
        return toResponse(model);
    }

    @Transactional
    public AiModelDtos.Response resumeModel(Long id, UserPrincipal actor) {
        AiModel model = modelRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("AI model not found"));
        Map<String, Object> old = toAuditSnapshot(model);
        model.setActive(true);
        modelRepository.save(model);
        auditService.log(AuditAction.AI_MODEL_RESUMED, actor, "AI_MODEL", id, old, toAuditSnapshot(model));
        return toResponse(model);
    }


    @Transactional
    public AiModelDtos.Response setDefault(Long id, UserPrincipal actor) {
        AiModel newDefault = modelRepository.findByIdAndActiveTrueAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException(
                        "Target model not found or is not active. Only active models can be set as default."));

        if (newDefault.isDefaultModel()) {
            return toResponse(newDefault);
        }

        AiModel prevDefault = modelRepository.findByDefaultModelTrueAndDeletedFalse()
                .orElse(null);

        Map<String, Object> oldSnapshot = prevDefault != null
                ? Map.of("id", prevDefault.getId(), "name", prevDefault.getName())
                : Map.of("note", "no previous default");

        if (prevDefault != null) {
            prevDefault.setDefaultModel(false);
            modelRepository.save(prevDefault);
        }

        newDefault.setDefaultModel(true);
        modelRepository.save(newDefault);

        log.info("Default model switched to {} by {}", newDefault.getName(), actor.getEmail());
        auditService.log(AuditAction.DEFAULT_MODEL_CHANGED, actor, "AI_MODEL", id,
                oldSnapshot,
                Map.of("id", newDefault.getId(), "name", newDefault.getName()));

        return toResponse(newDefault);
    }


    @Transactional
    public int deleteModel(Long id, UserPrincipal actor) {
        AiModel model = modelRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("AI model not found"));

        if (model.isDefaultModel()) {
            throw new BadRequestException(
                    "Cannot delete the default model. Set another model as default first.");
        }

        AiModel defaultModel = modelRepository.findByDefaultModelTrueAndDeletedFalse()
                .orElseThrow(() -> new BadRequestException(
                        "No default model configured. Cannot safely delete this model."));

        List<CodeRepository> affected = repoRepository.findByAiModel(model);
        for (CodeRepository repo : affected) {
            repo.setAiModel(defaultModel);
            repoRepository.save(repo);
        }

        Map<String, Object> oldSnapshot = toAuditSnapshot(model);
        model.setDeleted(true);
        model.setActive(false);
        modelRepository.save(model);

        log.info("AI model {} soft-deleted by {}. {} repos migrated to default '{}'.",
                id, actor.getEmail(), affected.size(), defaultModel.getName());

        auditService.logDelete(AuditAction.AI_MODEL_DELETED, actor, "AI_MODEL", id, oldSnapshot);

        if (!affected.isEmpty()) {
            List<ModelMigrationEvent.AffectedRepository> affectedDtos = affected.stream()
                    .map(repo -> new ModelMigrationEvent.AffectedRepository(
                            repo.getId(),
                            repo.getTitle(),
                            repo.getUser().getEmail(),
                            repo.getUser().getName()))
                    .toList();
            eventPublisher.publishEvent(new ModelMigrationEvent(affectedDtos, defaultModel.getName()));
        }

        return affected.size();
    }


    public AiModel requireDefaultModel() {
        return modelRepository.findByDefaultModelTrueAndDeletedFalse()
                .orElseThrow(() -> new BadRequestException(
                        "No default AI model is configured. Please contact the administrator."));
    }

    private AiModel findActiveModel(Long id) {
        return modelRepository.findByIdAndActiveTrueAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("AI model not found or not active"));
    }

    private void demoteCurrentDefault() {
        modelRepository.findByDefaultModelTrueAndDeletedFalse().ifPresent(current -> {
            current.setDefaultModel(false);
            modelRepository.save(current);
        });
    }

    public AiModelDtos.Response toResponse(AiModel m) {
        return AiModelDtos.Response.builder()
                .id(m.getId())
                .name(m.getName())
                .provider(m.getProvider())
                .description(m.getDescription())
                .active(m.isActive())
                .defaultModel(m.isDefaultModel())
                .apiKeyMask("••••••••")         
                .apiBaseUrl(m.getApiBaseUrl())
                .systemPrompt(m.getSystemPrompt())
                .temperature(m.getTemperature())
                .maxTokens(m.getMaxTokens())
                .createdByEmail(m.getCreatedBy() != null ? m.getCreatedBy().getEmail() : null)
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private Map<String, Object> toAuditSnapshot(AiModel m) {
        return Map.of(
                "id",           m.getId(),
                "name",         m.getName(),
                "provider",     m.getProvider(),
                "active",       m.isActive(),
                "defaultModel", m.isDefaultModel(),
                "deleted",      m.isDeleted()
        );
    }
}