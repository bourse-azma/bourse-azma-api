package com.ernoxin.bourseazmaapi.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RevokedTokenService {

    private static final String REVOKED_TOKEN_PREFIX = "auth:revoked:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String token, Instant expiresAt) {
        if (token == null || token.isBlank()) {
            return;
        }
        long ttlSeconds = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
        redisTemplate.opsForValue().set(revokedTokenKey(token), "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(revokedTokenKey(token)));
    }

    private String revokedTokenKey(String token) {
        return REVOKED_TOKEN_PREFIX + sha256(token);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
