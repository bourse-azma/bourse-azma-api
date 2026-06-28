package com.ernoxin.bourseazmaapi.controller;

import com.ernoxin.bourseazmaapi.dto.api.ApiResponse;
import com.ernoxin.bourseazmaapi.dto.auth.AuthTokenResponse;
import com.ernoxin.bourseazmaapi.dto.auth.LoginRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;
import com.ernoxin.bourseazmaapi.security.AuthCookieService;
import com.ernoxin.bourseazmaapi.service.AuthService;
import com.ernoxin.bourseazmaapi.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService authCookieService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthTokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthTokenResponse tokenResponse = authService.register(request);
        authCookieService.setAccessTokenCookie(
                response,
                tokenResponse.accessToken(),
                tokenResponse.accessTokenExpiresAt(),
                false
        );
        return ApiResponse.of(HttpStatus.CREATED, "عملیات با موفقیت انجام شد", tokenResponse.withoutToken());
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        AuthTokenResponse tokenResponse = authService.login(request, ClientIpResolver.resolve(httpRequest));
        authCookieService.setAccessTokenCookie(
                response,
                tokenResponse.accessToken(),
                tokenResponse.accessTokenExpiresAt(),
                request.isRememberMe()
        );
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", tokenResponse.withoutToken());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        String bearerToken = authCookieService.extractAccessToken(request, authorization);
        authService.logout(bearerToken);
        authCookieService.clearAccessTokenCookie(response);
        return ApiResponse.of(HttpStatus.OK, "عملیات با موفقیت انجام شد", null);
    }
}
