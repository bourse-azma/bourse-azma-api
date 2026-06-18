package com.ernoxin.boorsazmaapi.controller;

import com.ernoxin.boorsazmaapi.dto.api.ApiResponse;
import com.ernoxin.boorsazmaapi.dto.auth.AuthTokenResponse;
import com.ernoxin.boorsazmaapi.dto.auth.LoginRequest;
import com.ernoxin.boorsazmaapi.dto.auth.RegisterRequest;
import com.ernoxin.boorsazmaapi.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", authService.login(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String bearerToken = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            bearerToken = authorization.substring(7);
        }
        authService.logout(bearerToken);
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", null);
    }
}
