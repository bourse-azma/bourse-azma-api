package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.auth.AuthTokenResponse;
import com.ernoxin.boorsazmaapi.dto.auth.LoginRequest;
import com.ernoxin.boorsazmaapi.dto.auth.RegisterRequest;

public interface AuthService {
    AuthTokenResponse register(RegisterRequest request);

    AuthTokenResponse login(LoginRequest request);

    void logout(String bearerToken);
}
