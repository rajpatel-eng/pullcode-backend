package com.capstoneproject.codereviewsystem.services.user;

import com.capstoneproject.codereviewsystem.dtos.OtpResponse;
import com.capstoneproject.codereviewsystem.dtos.ProfileResponse;
import com.capstoneproject.codereviewsystem.dtos.UserRequest;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.CliCommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.CommitHistoryRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.repos.ZipUploadHistoryRepository;
import com.capstoneproject.codereviewsystem.services.auth.OtpService;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import com.capstoneproject.codereviewsystem.services.storage.StorageProvider;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final CommitHistoryRepository commitHistoryRepository;
    private final ZipUploadHistoryRepository zipUploadHistoryRepository;
    private final CliCommitHistoryRepository cliCommitHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final EmailContentService emailContentService;
    private final StorageProvider storageProvider;

    public ProfileResponse getProfile(Long userId) {
        User user = findUser(userId);

        long githubReviews = commitHistoryRepository.countByUser(user);
        long zipReviews = zipUploadHistoryRepository.countByUser(user);
        long cliReviews = cliCommitHistoryRepository.countByUser(user);

        return ProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .authProvider(user.getAuthProvider().name())
                .githubReviews(githubReviews)
                .zipReviews(zipReviews)
                .cliReviews(cliReviews)
                .totalReviews(githubReviews + zipReviews + cliReviews)
                .build();
    }

    @Transactional
    public ProfileResponse updateName(Long userId, UserRequest.UpdateName req) {
        User user = findUser(userId);
        user.setName(req.getName());
        userRepository.save(user);
        log.info("Name updated for user: {}", user.getEmail());
        return getProfile(userId);
    }

    @Transactional
    public ProfileResponse updateProfilePhoto(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("File must be an image (jpg, png, gif, webp)");
        }

        User user = findUser(userId);

        String ext = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + "." + ext;
        String avatarRelativePath = "avatars/" + filename;

        try {
            // Delete old avatar if present
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
                String oldFilename = user.getAvatarUrl()
                        .substring(user.getAvatarUrl().lastIndexOf("/") + 1);
                storageProvider.deleteFile("avatars/" + oldFilename);
            }

            storageProvider.saveFile(avatarRelativePath, file);

        } catch (IOException e) {
            log.error("Failed to save avatar: {}", e.getMessage());
            throw new BadRequestException("Failed to save image. Please try again.");
        }

        String url = storageProvider.getPublicUrl(avatarRelativePath);

        user.setAvatarUrl(url);
        userRepository.save(user);

        log.info("Avatar updated for user: {}", user.getEmail());

        return getProfile(userId);
    }

    @Transactional
    public void changePassword(Long userId, UserRequest.ChangePassword req) {
        User user = findUser(userId);

        if (user.getPassword() == null) {
            throw new BadRequestException(
                    "OAuth accounts cannot change password this way. Please use forgot password.");
        }

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect.");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        emailService.sendEmail(
                user.getEmail(),
                emailContentService.passwordChangedSubject(),
                emailContentService.passwordChangedBody(user.getName()));

        log.info("Password changed for user: {}", user.getEmail());
    }


    public OtpResponse sendForgotPasswordOtp(UserRequest.ForgotPasswordSendOtp req) {
        if (!userRepository.existsByEmail(req.getEmail())) {
            // Don't reveal whether email exists — return same message
            return new OtpResponse("If this email is registered, an OTP has been sent.", req.getEmail());
        }

        if (otpService.hasActiveForgotOtp(req.getEmail())) {
            long remaining = otpService.getForgotOtpRemainingTtlSeconds(req.getEmail());
            throw new BadRequestException(
                    "OTP already sent. Wait " + remaining + " seconds before requesting a new one.");
        }

        String otp = otpService.generateAndStoreForgotOtp(req.getEmail());
        emailService.sendEmail(
                req.getEmail(),
                emailContentService.forgotPasswordSubject(),
                emailContentService.forgotPasswordBody(otp));

        log.info("Forgot-password OTP sent to: {}", req.getEmail());
        return new OtpResponse("If this email is registered, an OTP has been sent.", req.getEmail());
    }

    @Transactional
    public void resetPassword(UserRequest.ForgotPasswordReset req) {
        if (!otpService.verifyForgotOtp(req.getEmail(), req.getOtp())) {
            throw new BadRequestException("Invalid or expired OTP. Please request a new one.");
        }

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found."));

        if (user.getPassword() != null && passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        emailService.sendEmail(
                user.getEmail(),
                emailContentService.passwordChangedSubject(),
                emailContentService.passwordChangedBody(user.getName()));

        log.info("Password reset via OTP for: {}", req.getEmail());
    }


    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found."));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}