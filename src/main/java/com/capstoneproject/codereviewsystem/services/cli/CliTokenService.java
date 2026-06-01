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
                User user = getUser(userId);
                ZipProject project = getProject(projectId, user);

                String tokenName = (req.getName() != null && !req.getName().isBlank())
                                ? req.getName().trim()
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
                log.info("Token generated: project={} user={} tokenId={} name={}",
                                projectId, userId, cliToken.getId(), tokenName);

                return toResponse(cliToken);
        }

        public List<CliTokenResponse> getProjectTokens(Long projectId, Long userId) {
                User user = getUser(userId);
                ZipProject project = getProject(projectId, user);

                return cliTokenRepository.findByZipProjectAndUserOrderByCreatedAtDesc(project, user)
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Transactional
        public CliTokenResponse renameToken(Long projectId, Long tokenId, RenameTokenRequest req, Long userId) {
                User user = getUser(userId);
                ZipProject project = getProject(projectId, user);

                if (req.getName() == null || req.getName().isBlank()) {
                        throw new BadRequestException("Token name is required");
                }

                CliToken token = getToken(tokenId, project, user);
                token.setName(req.getName().trim());
                cliTokenRepository.save(token);
                log.info("Token renamed: tokenId={} project={}", tokenId, projectId);
                return toResponse(token);
        }

        @Transactional
        public CliTokenResponse toggleTokenStatus(Long projectId, Long tokenId, Long userId) {
                User user = getUser(userId);
                ZipProject project = getProject(projectId, user);

                CliToken token = getToken(tokenId, project, user);
                token.setActive(!token.isActive());
                cliTokenRepository.save(token);
                log.info("Token {}: tokenId={} project={}",
                                token.isActive() ? "resumed" : "paused", tokenId, projectId);
                return toResponse(token);
        }

        @Transactional
        public void deleteToken(Long projectId, Long tokenId, Long userId) {
                User user = getUser(userId);
                ZipProject project = getProject(projectId, user);

                CliToken token = getToken(tokenId, project, user);
                cliTokenRepository.delete(token);
                log.info("Token deleted: tokenId={} project={}", tokenId, projectId);
        }


        public record TokenValidationResult(CliToken token, User user, ZipProject project) {
        }

        @Transactional
        public TokenValidationResult validateAndTouch(String rawToken) {
                CliToken token = cliTokenRepository.findByTokenAndActiveTrue(rawToken)
                                .orElseThrow(() -> new BadRequestException(
                                                "Invalid, revoked, or paused CLI token."));

                token.setLastUsedAt(LocalDateTime.now());
                cliTokenRepository.save(token);

                return new TokenValidationResult(token, token.getUser(), token.getZipProject());
        }


        private User getUser(Long userId) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> new BadRequestException("User not found"));
        }

        private ZipProject getProject(Long projectId, User user) {
                return zipProjectRepository.findByIdAndUser(projectId, user)
                                .orElseThrow(() -> new BadRequestException("Project not found or access denied"));
        }

        private CliToken getToken(Long tokenId, ZipProject project, User user) {
                return cliTokenRepository.findByIdAndZipProjectAndUser(tokenId, project, user)
                                .orElseThrow(() -> new BadRequestException("Token not found"));
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