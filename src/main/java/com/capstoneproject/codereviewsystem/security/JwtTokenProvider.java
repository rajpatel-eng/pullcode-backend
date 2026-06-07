package com.capstoneproject.codereviewsystem.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @PostConstruct
    public void validateJwtSecret() {

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is missing");
        }

        if (jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateTokenFromEmail(String email) {
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
            .signWith(getSigningKey())
            .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser().verifyWith(getSigningKey())
            .build().parseSignedClaims(token)
            .getPayload().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.error("JWT validation error: {}", ex.getMessage());
            return false;
        }
    }
}
