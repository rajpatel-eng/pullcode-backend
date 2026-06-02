package com.capstoneproject.codereviewsystem.services.admin;

import com.capstoneproject.codereviewsystem.dtos.IamDtos;
import com.capstoneproject.codereviewsystem.dtos.enums.AuthProvider;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.dtos.enums.AuditAction;
import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.dtos.enums.UserStatus;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;
import com.capstoneproject.codereviewsystem.services.audit.AuditService;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class IamManagementService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailContentService emailContentService;
    private final AuditService auditService;


    @Transactional
    public IamDtos.Response createIam(IamDtos.CreateRequest req, UserPrincipal admin) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("Email already registered");
        }

        User adminUser = userRepository.findById(admin.getId())
                .orElseThrow(() -> new BadRequestException("Admin not found"));

        User iam = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true) // admin-created — no email verification needed
                .roles(Set.of(Role.ROLE_IAM))
                .status(UserStatus.ACTIVE)
                .createdBy(adminUser)
                .build();

        userRepository.save(iam);
        log.info("IAM user created: {} by admin: {}", iam.getEmail(), admin.getEmail());

        emailService.sendEmail(
                iam.getEmail(),
                emailContentService.iamWelcomeSubject(),
                emailContentService.iamWelcomeBody(iam.getName()));

        auditService.logCreate(AuditAction.IAM_CREATED, admin, "IAM_USER", iam.getId(),
                toAuditSnapshot(iam));

        return toResponse(iam);
    }


    public Page<IamDtos.Response> listIam(Pageable pageable) {
        return userRepository.findByRole(Role.ROLE_IAM, pageable)
                .map(this::toResponse);
    }

    public IamDtos.Response getIam(Long id) {
        User iam = findIamUser(id);
        return toResponse(iam);
    }


    @Transactional
    public IamDtos.Response updateName(Long id, IamDtos.UpdateNameRequest req,
            UserPrincipal admin) {
        User iam = findIamUser(id);
        Map<String, Object> old = toAuditSnapshot(iam);
        iam.setName(req.getName());
        userRepository.save(iam);
        auditService.log(AuditAction.IAM_UPDATED, admin, "IAM_USER", id, old, toAuditSnapshot(iam));
        return toResponse(iam);
    }


    @Transactional
    public IamDtos.Response updateEmail(Long id, IamDtos.UpdateEmailRequest req,
            UserPrincipal admin) {
        User iam = findIamUser(id);
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BadRequestException("Email already in use");
        }
        Map<String, Object> old = toAuditSnapshot(iam);
        iam.setEmail(req.getEmail());
        userRepository.save(iam);
        auditService.log(AuditAction.IAM_UPDATED, admin, "IAM_USER", id, old, toAuditSnapshot(iam));
        return toResponse(iam);
    }


    @Transactional
    public void resetPassword(Long id, IamDtos.ResetPasswordRequest req, UserPrincipal admin) {
        User iam = findIamUser(id);
        iam.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(iam);
        log.info("Password reset for IAM {} by admin {}", id, admin.getEmail());
        auditService.log(AuditAction.IAM_PASSWORD_RESET, admin, "IAM_USER", id,
                Map.of("action", "password_reset"),
                Map.of("action", "password_reset_complete"));
        emailService.sendEmail(
                iam.getEmail(),
                emailContentService.passwordChangedSubject(),
                emailContentService.passwordChangedBody(iam.getName()));
    }


    @Transactional
    public IamDtos.Response pauseIam(Long id, UserPrincipal admin) {
        User iam = findIamUser(id);
        if (iam.getStatus() == UserStatus.PAUSED) {
            throw new BadRequestException("IAM account is already paused");
        }
        Map<String, Object> old = toAuditSnapshot(iam);
        iam.setStatus(UserStatus.PAUSED);
        userRepository.save(iam);
        auditService.log(AuditAction.IAM_PAUSED, admin, "IAM_USER", id, old, toAuditSnapshot(iam));
        return toResponse(iam);
    }


    @Transactional
    public IamDtos.Response resumeIam(Long id, UserPrincipal admin) {
        User iam = findIamUser(id);
        if (iam.getStatus() == UserStatus.ACTIVE) {
            throw new BadRequestException("IAM account is already active");
        }
        if (iam.getStatus() == UserStatus.DELETED) {
            throw new BadRequestException("Cannot resume a deleted IAM account");
        }
        Map<String, Object> old = toAuditSnapshot(iam);
        iam.setStatus(UserStatus.ACTIVE);
        userRepository.save(iam);
        auditService.log(AuditAction.IAM_RESUMED, admin, "IAM_USER", id, old, toAuditSnapshot(iam));
        return toResponse(iam);
    }


    @Transactional
    public void deleteIam(Long id, UserPrincipal admin) {
        User iam = findIamUser(id);
        if (iam.getStatus() == UserStatus.DELETED) {
            throw new BadRequestException("IAM account is already deleted");
        }
        Map<String, Object> old = toAuditSnapshot(iam);
        iam.setStatus(UserStatus.DELETED);
        userRepository.save(iam);
        log.info("IAM user {} soft-deleted by admin {}", id, admin.getEmail());
        auditService.logDelete(AuditAction.IAM_DELETED, admin, "IAM_USER", id, old);
    }


    private User findIamUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("IAM user not found"));

        if (!user.getRoles().contains(Role.ROLE_IAM)) {
            throw new BadRequestException("User is not an IAM account");
        }

        return user;
    }

    public IamDtos.Response toResponse(User u) {
        return IamDtos.Response.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .status(u.getStatus())
                .createdByEmail(u.getCreatedBy() != null ? u.getCreatedBy().getEmail() : null)
                .createdAt(u.getCreatedBy() != null ? null : null) // set if User has createdAt
                .build();
    }

    private Map<String, Object> toAuditSnapshot(User u) {
        return Map.of(
                "id", u.getId(),
                "name", u.getName(),
                "email", u.getEmail(),
                "status", u.getStatus().name());
    }
}