package com.capstoneproject.codereviewsystem.services.cli;

import com.capstoneproject.codereviewsystem.dtos.CliDtos.*;
import com.capstoneproject.codereviewsystem.entity.CliToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.entity.ZipProject;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.CliTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.repos.ZipProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CliTokenService {

    private final CliTokenRepository cliTokenRepository;
    private final UserRepository userRepository;
    private final ZipProjectRepository zipProjectRepository;

   
    @Transactional
    public CliTokenResponse generateToken(Long projectId, Long userId, GenerateTokenRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found or access denied"));

        String tokenName = (req.getName() != null && !req.getName().isBlank())
                ? req.getName()
                : "Token-" + System.currentTimeMillis();

        String rawToken = "crk_" + UUID.randomUUID().toString().replace("-", "");

        CliToken cliToken = CliToken.builder()
                .token(rawToken)
                .name(tokenName)
                .user(user)
                .zipProject(project)
                .active(true)
                .build();

        cliToken = cliTokenRepository.save(cliToken);
        log.info("CLI token generated: project={} user={} tokenId={}", projectId, userId, cliToken.getId());

        return toResponse(cliToken);
    }


    @Transactional
    public CliTokenResponse rotateToken(Long projectId, Long userId, String newTokenValue) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found or access denied"));

        CliToken newToken = cliTokenRepository.findByTokenAndActiveTrue(newTokenValue)
                .orElseThrow(() -> new BadRequestException(
                        "Token not found or already revoked. Generate a new token from the Project UI first."));

        List<CliToken> oldTokens = cliTokenRepository.findByZipProjectAndUserAndActiveTrue(project, user);
        oldTokens.stream()
                .filter(t -> !t.getToken().equals(newTokenValue))
                .forEach(t -> t.setActive(false));
        cliTokenRepository.saveAll(oldTokens);

        log.info("CLI token rotated: project={} user={} new tokenId={}", projectId, userId, newToken.getId());
        return toResponse(newToken);
    }


    @Transactional
    public void revokeToken(Long projectId, Long tokenId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        CliToken token = cliTokenRepository.findByIdAndZipProjectAndUser(tokenId, project, user)
                .orElseThrow(() -> new BadRequestException("Token not found"));

        token.setActive(false);
        cliTokenRepository.save(token);
        log.info("CLI token revoked: tokenId={} project={}", tokenId, projectId);
    }


    public List<CliTokenResponse> getProjectTokens(Long projectId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        ZipProject project = zipProjectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new BadRequestException("Project not found"));

        return cliTokenRepository.findByZipProjectAndActiveTrueOrderByCreatedAtDesc(project)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }


    public record TokenValidationResult(CliToken token, User user, ZipProject project) {}

    @Transactional
    public TokenValidationResult validateAndTouch(String rawToken) {
        CliToken token = cliTokenRepository.findByTokenAndActiveTrue(rawToken)
                .orElseThrow(() -> new BadRequestException(
                        "Invalid or revoked CLI token. Run: code-rakshak init -t <token>"));

        token.setLastUsedAt(LocalDateTime.now());
        cliTokenRepository.save(token);

        return new TokenValidationResult(token, token.getUser(), token.getZipProject());
    }


    private CliTokenResponse toResponse(CliToken t) {
        return CliTokenResponse.builder()
                .id(t.getId())
                .token(t.getToken())
                .name(t.getName())
                .projectId(t.getZipProject().getId())
                .projectTitle(t.getZipProject().getTitle())
                .createdAt(t.getCreatedAt())
                .lastUsedAt(t.getLastUsedAt())
                .active(t.isActive())
                .build();
    }
}
