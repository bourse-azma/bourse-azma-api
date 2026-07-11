package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void init() {
        // Always generate a strong random signing key at startup.
        // Previous JWTs are invalidated on restart (acceptable for access tokens).
        byte[] keyBytes = new byte[64]; // 512-bit key for strong HMAC
        new SecureRandom().nextBytes(keyBytes);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(AppUserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getAccessTokenMinutes() * 60L);
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("role", principal.getRole().name())
                .claim("username", principal.getUsername())
                .claim("email", principal.getEmail())
                .claim("ver", principal.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public Instant accessTokenExpiresAt() {
        return Instant.now().plusSeconds(jwtProperties.getAccessTokenMinutes() * 60L);
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public Instant extractExpiration(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        return expiration.toInstant();
    }

    public long extractTokenVersion(String token) {
        Number version = parseClaims(token).get("ver", Number.class);
        return version == null ? 0L : version.longValue();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (SecurityException ex) {
            throw ex;
        }
    }
}
