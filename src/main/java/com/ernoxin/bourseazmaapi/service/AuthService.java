package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.auth.AuthTokenResponse;
import com.ernoxin.bourseazmaapi.dto.auth.LoginRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;

public interface AuthService {
    AuthTokenResponse register(RegisterRequest request, String clientIp);

    AuthTokenResponse login(LoginRequest request, String clientIp);

    void logout(String bearerToken);
}
