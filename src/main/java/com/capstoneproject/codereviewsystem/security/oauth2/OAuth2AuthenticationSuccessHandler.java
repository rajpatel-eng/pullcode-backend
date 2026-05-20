package com.capstoneproject.codereviewsystem.security.oauth2;

import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.RefreshTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.JwtTokenProvider;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenDurationMs;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String accessToken = jwtTokenProvider.generateTokenFromEmail(userPrincipal.getEmail());
        RefreshToken refreshToken = createRefreshToken(userPrincipal.getId());

        Cookie accessTokenCookie = new Cookie("accessToken", accessToken);
        accessTokenCookie.setHttpOnly(true);
        accessTokenCookie.setPath("/");
        accessTokenCookie.setMaxAge((int) (accessTokenExpirationMs / 1000)); // 15 min
        response.addCookie(accessTokenCookie);

        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken.getToken());
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge((int) (refreshTokenDurationMs / 1000)); // 7 days
        response.addCookie(refreshTokenCookie);

        log.info("OAuth2 login success for: {} — tokens stored in cookies",
                userPrincipal.getEmail());

        clearAuthenticationAttributes(request);

        getRedirectStrategy().sendRedirect(request, response, "/api/user/profile");
    }

    private RefreshToken createRefreshToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        refreshTokenRepository.findByUser(user)
                .ifPresent(refreshTokenRepository::delete);

        return refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        .token(UUID.randomUUID().toString())
                        .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                        .build()
        );
    }
}