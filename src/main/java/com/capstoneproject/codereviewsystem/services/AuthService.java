package com.capstoneproject.codereviewsystem.services;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.capstoneproject.codereviewsystem.dtos.AuthProvider;
import com.capstoneproject.codereviewsystem.dtos.AuthRequest;
import com.capstoneproject.codereviewsystem.dtos.AuthResponse;
import com.capstoneproject.codereviewsystem.dtos.Role;
import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.exceptions.BadRequestException;
import com.capstoneproject.codereviewsystem.exceptions.TokenRefreshException;
import com.capstoneproject.codereviewsystem.repos.RefreshTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.JwtTokenProvider;

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

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenDurationMs;

    public AuthResponse register(AuthRequest.Register req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new BadRequestException("Email already registered");

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .roles(Set.of(Role.ROLE_USER))
                .build();
        userRepository.save(user);

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

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account with that email"));

        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        log.info("Password reset token for {}: {}", email, resetToken);
    }

    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Reset token has expired");

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
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