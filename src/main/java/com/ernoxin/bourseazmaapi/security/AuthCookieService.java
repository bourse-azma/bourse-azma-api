package com.ernoxin.bourseazmaapi.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthCookieService {

    public static final String ACCESS_TOKEN_COOKIE = "bourse_azma_access_token";

    @Value("${app.security.cookie.secure:true}")
    private boolean secureCookie;

    public void setAccessTokenCookie(HttpServletResponse response, String token, Instant expiresAt, boolean persistent) {
        long maxAgeSeconds = Math.max(0, Duration.between(Instant.now(), expiresAt).getSeconds());
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(persistent ? maxAgeSeconds : -1)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String extractAccessToken(HttpServletRequest request, String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_TOKEN_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
