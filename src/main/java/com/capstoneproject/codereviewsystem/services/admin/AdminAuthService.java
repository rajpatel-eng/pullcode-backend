package com.capstoneproject.codereviewsystem.services.admin;

import com.capstoneproject.codereviewsystem.dtos.AdminAuthDtos;
import com.capstoneproject.codereviewsystem.dtos.AuthResponse;
import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.dtos.enums.UserStatus;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.repos.RefreshTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.JwtTokenProvider;
import com.capstoneproject.codereviewsystem.security.PreAuthTokenService;
import com.capstoneproject.codereviewsystem.services.auth.OtpService;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuthService {

    private final UserRepository          userRepository;
    private final PasswordEncoder         passwordEncoder;
    private final JwtTokenProvider        jwtTokenProvider;
    private final PreAuthTokenService     preAuthTokenService;
    private final OtpService              otpService;
    private final EmailService            emailService;
    private final EmailContentService     emailContentService;
    private final RefreshTokenRepository  refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenDurationMs;

    private static final String ADMIN_OTP_PREFIX = "ADMIN_OTP:";


    public AdminAuthDtos.PreAuthResponse login(AdminAuthDtos.LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        boolean isAdminOrIam = user.getRoles().contains(Role.ROLE_ADMIN)
                || user.getRoles().contains(Role.ROLE_IAM);
        if (!isAdminOrIam) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (user.getStatus() == UserStatus.PAUSED) {
            throw new BadCredentialsException("Account is paused. Contact administrator.");
        }
        if (user.getStatus() == UserStatus.DELETED) {
            throw new BadCredentialsException("Account does not exist.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String otp = otpService.generateAdminOtp(user.getId());

        emailService.sendEmail(
                user.getEmail(),
                emailContentService.adminOtpSubject(),
                emailContentService.adminOtpBody(user.getName(), otp)
        );

        String preAuthToken = preAuthTokenService.generatePreAuthToken(user.getId(), user.getEmail());
        log.info("Admin/IAM login step-1 success for: {}", user.getEmail());

        return new AdminAuthDtos.PreAuthResponse(preAuthToken,
                "OTP sent to your registered email. Valid for 5 minutes.");
    }


    @Transactional
    public AuthResponse verifyOtp(AdminAuthDtos.OtpVerifyRequest req) {
        Long userId = preAuthTokenService.extractUserIdIfValid(req.getPreAuthToken());
        if (userId == null) {
            throw new BadRequestException("Pre-auth token is invalid or expired. Please login again.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (!otpService.verifyAdminOtp(userId, req.getOtp())) {
            throw new BadRequestException("Invalid or expired OTP.");
        }

        String accessToken  = jwtTokenProvider.generateTokenFromEmail(user.getEmail());
        String refreshToken = createRefreshToken(user);

        log.info("Admin/IAM login step-2 success for: {}", user.getEmail());
        return new AuthResponse(accessToken, refreshToken, user.getEmail());
    }


    private String createRefreshToken(User user) {
        RefreshToken token = refreshTokenRepository.findByUser(user)
                .orElseGet(RefreshToken::new);
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshTokenRepository.save(token);
        return token.getToken();
    }
}