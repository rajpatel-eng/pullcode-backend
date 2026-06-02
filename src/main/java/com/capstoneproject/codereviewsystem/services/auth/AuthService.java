package com.capstoneproject.codereviewsystem.services.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.capstoneproject.codereviewsystem.dtos.enums.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.enums.Role;
import com.capstoneproject.codereviewsystem.dtos.AuthRequest;
import com.capstoneproject.codereviewsystem.dtos.AuthResponse;
import com.capstoneproject.codereviewsystem.dtos.OtpResponse;
import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.exceptions.TokenRefreshException;
import com.capstoneproject.codereviewsystem.repos.RefreshTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.JwtTokenProvider;
import com.capstoneproject.codereviewsystem.services.email.EmailContentService;
import com.capstoneproject.codereviewsystem.services.email.EmailService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final EmailContentService emailContentService;
    private final OtpService otpService;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenDurationMs;

    public OtpResponse sendOtp(String email) {

        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Email already registered");

        if (otpService.hasActiveOtp(email)) {
            long remaining = otpService.getRemainingTtlSeconds(email);
            throw new BadRequestException(
                    "OTP already sent. Wait " + remaining + " seconds before requesting a new one.");
        }

        String otp = otpService.generateAndStoreOtp(email);
        emailService.sendEmail(
                email,
                emailContentService.otpSubject(),
                emailContentService.otpBody(otp));

        log.info("OTP sent to: {}", email);
        return new OtpResponse("OTP sent to your email. Valid for 10 minutes.", email);
    }

    @Transactional
    public AuthResponse register(AuthRequest.Register req) {

        if (userRepository.existsByEmail(req.getEmail()))
            throw new BadRequestException("Email already registered");

        if (!otpService.verifyOtp(req.getEmail(), req.getOtp()))
            throw new BadRequestException("Invalid or expired OTP. Please request a new one.");

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .roles(Set.of(Role.ROLE_USER))
                .build();
        userRepository.save(user);

        emailService.sendEmail(
                req.getEmail(),
                emailContentService.welcomeSubject(),
                emailContentService.welcomeBody(req.getName()));

        log.info("User registered and verified: {}", req.getEmail());

        return buildAuthResponse(user);
    }

    public AuthResponse login(AuthRequest.Login req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));

        User user = userRepository.findByEmail(req.getEmail()).orElseThrow();
        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        RefreshToken token = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token expired, please login again");
        }

        User user = token.getUser();
        refreshTokenRepository.delete(token);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        refreshTokenRepository.deleteByUser(user);
    }

    public RefreshToken createRefreshTokenForUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return createRefreshToken(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateTokenFromEmail(user.getEmail());
        RefreshToken refreshToken = createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), user.getEmail());
    }

    private RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.findByUser(user)
                .ifPresent(refreshTokenRepository::delete);

        return refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .build());
    }
}