package com.ernoxin.bourseazmaapi.dto.auth;

import com.ernoxin.bourseazmaapi.model.UserRole;

import java.time.Instant;

public record AuthTokenResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        Long userId,
        UserRole role
) {
    public AuthTokenResponse withoutToken() {
        return new AuthTokenResponse(null, accessTokenExpiresAt, userId, role);
    }
}
