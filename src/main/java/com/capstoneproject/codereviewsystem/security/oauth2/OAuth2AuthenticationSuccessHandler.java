package com.capstoneproject.codereviewsystem.security.oauth2;

import com.capstoneproject.codereviewsystem.entity.RefreshToken;
import com.capstoneproject.codereviewsystem.entity.User;
import com.capstoneproject.codereviewsystem.repos.RefreshTokenRepository;
import com.capstoneproject.codereviewsystem.repos.UserRepository;
import com.capstoneproject.codereviewsystem.security.JwtTokenProvider;
import com.capstoneproject.codereviewsystem.security.UserPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieRepo;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenDurationMs;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String defaultFrontendUrl;

    /**
     * Resolve the frontend origin to redirect to after OAuth success.
     *
     * Priority:
     *  1. The oauth2_redirect_uri cookie we saved when the flow started
     *     (set by HttpCookieOAuth2AuthorizationRequestRepository).
     *  2. The app.frontend-url property / FRONTEND_URL env var as fallback.
     *
     * We deliberately do NOT use Origin/Referer headers here because by the
     * time Google calls our callback the headers belong to accounts.google.com.
     */
    private String resolveFrontendOrigin(HttpServletRequest request) {
        // 1. Cookie set at flow start — most reliable
        String fromCookie = cookieRepo
                .getCookieValue(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_COOKIE)
                .orElse(null);

        if (fromCookie != null && !fromCookie.isBlank()) {
            log.debug("Resolved frontend origin from cookie: {}", fromCookie);
            return fromCookie;
        }

        // 2. Config / env-var fallback
        String envUrl = System.getenv("FRONTEND_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            log.debug("Resolved frontend origin from FRONTEND_URL env: {}", envUrl);
            return envUrl;
        }

        log.debug("Resolved frontend origin from app.frontend-url property: {}", defaultFrontendUrl);
        return defaultFrontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String accessToken    = jwtTokenProvider.generateTokenFromEmail(userPrincipal.getEmail());
        RefreshToken refresh  = createRefreshToken(userPrincipal.getId());

        String frontendOrigin = resolveFrontendOrigin(request);

        log.info("OAuth2 login success for {} — redirecting to {}/login-success",
                userPrincipal.getEmail(), frontendOrigin);

        clearAuthenticationAttributes(request);

        // Build: <frontendOrigin>/login-success?token=...&refreshToken=...&email=...
        String redirectUrl = UriComponentsBuilder
                .fromUriString(frontendOrigin + "/login-success")
                .queryParam("token",        accessToken)
                .queryParam("refreshToken", refresh.getToken())
                .queryParam("email",        userPrincipal.getEmail())
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
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
