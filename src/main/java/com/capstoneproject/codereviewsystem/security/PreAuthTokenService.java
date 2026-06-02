package com.capstoneproject.codereviewsystem.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;


@Service
@Slf4j
public class PreAuthTokenService {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE_VALUE = "PRE_AUTH";
    private static final long   PRE_AUTH_EXPIRY_MS = 5 * 60 * 1000L; // 5 minutes

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    public String generatePreAuthToken(Long userId, String email) {
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_VALUE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PRE_AUTH_EXPIRY_MS))
                .signWith(getKey())
                .compact();
    }

    public Long extractUserIdIfValid(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE_VALUE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                log.warn("Token is not a PRE_AUTH token");
                return null;
            }

            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.warn("Invalid or expired preAuthToken: {}", e.getMessage());
            return null;
        }
    }

    private SecretKey getKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        byte[] paddedKey = new byte[32];
        System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
        return Keys.hmacShaKeyFor(paddedKey);
    }
}