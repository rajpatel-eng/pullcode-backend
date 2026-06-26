package com.capstoneproject.codereviewsystem.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.URI;
import java.util.Base64;
import java.util.Optional;

@Component
@Slf4j
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String OAUTH2_COOKIE_NAME    = "oauth2_auth_request";
    // Stores the frontend origin so the success handler can redirect back correctly.
    // This is needed because after Google's redirect the Origin/Referer headers
    // belong to accounts.google.com, not our frontend.
    public  static final String REDIRECT_URI_COOKIE   = "oauth2_redirect_uri";
    private static final int    COOKIE_EXPIRE_SECONDS = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookieValue(request, OAUTH2_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authorizationRequest == null) {
            deleteCookie(request, response, OAUTH2_COOKIE_NAME);
            deleteCookie(request, response, REDIRECT_URI_COOKIE);
            return;
        }

        addCookie(response, OAUTH2_COOKIE_NAME,
                serialize(authorizationRequest), COOKIE_EXPIRE_SECONDS, request);

        // Save the frontend origin in a separate cookie.
        // The frontend calls /oauth2/authorize/google?redirect_uri=http://localhost:5173/login-success
        // We extract the origin part (scheme + host) and store it.
        String redirectUriParam = request.getParameter("redirect_uri");
        if (redirectUriParam != null && !redirectUriParam.isBlank()) {
            try {
                URI uri = URI.create(redirectUriParam);
                String frontendOrigin = uri.getScheme() + "://" + uri.getAuthority();
                addCookie(response, REDIRECT_URI_COOKIE, frontendOrigin, COOKIE_EXPIRE_SECONDS, request);
                log.debug("Saved frontend origin cookie: {}", frontendOrigin);
            } catch (Exception e) {
                log.warn("Could not parse redirect_uri param '{}': {}", redirectUriParam, e.getMessage());
            }
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        return loadAuthorizationRequest(request);
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    public Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private void addCookie(HttpServletResponse response, String name, String value,
                           int maxAge, HttpServletRequest request) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    cookie.setValue("");
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private String serialize(OAuth2AuthorizationRequest request) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(request);
            return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            log.error("Failed to serialize OAuth2AuthorizationRequest", e);
            throw new RuntimeException("OAuth2 cookie serialization failed", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                Object obj = ois.readObject();
                if (obj instanceof OAuth2AuthorizationRequest authRequest) {
                    return authRequest;
                }
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_cookie"), "Invalid OAuth2 cookie type");
            }
        } catch (Exception e) {
            log.error("Failed to deserialize OAuth2 cookie", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("cookie_deserialization_failed"),
                    "Failed to deserialize OAuth2 cookie", e);
        }
    }
}
