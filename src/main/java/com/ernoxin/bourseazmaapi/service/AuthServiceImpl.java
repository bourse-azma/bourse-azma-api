package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.auth.AuthTokenResponse;
import com.ernoxin.bourseazmaapi.dto.auth.LoginRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;
import com.ernoxin.bourseazmaapi.exception.InvalidCredentialsException;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import com.ernoxin.bourseazmaapi.security.JwtTokenService;
import com.ernoxin.bourseazmaapi.security.LoginAttemptService;
import com.ernoxin.bourseazmaapi.security.RevokedTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RevokedTokenService revokedTokenService;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        userService.register(request);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setIdentifier(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest, "unknown");
    }

    @Override
    @Transactional
    public AuthTokenResponse login(LoginRequest request, String clientIp) {
        String normalizedIdentifier = request.getIdentifier().trim().toLowerCase(Locale.ROOT);
        loginAttemptService.ensureLoginAllowed(clientIp);

        User user = userRepository.findByUsernameOrEmail(normalizedIdentifier, normalizedIdentifier)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailedLogin(clientIp);
            throw new InvalidCredentialsException();
        }

        loginAttemptService.clearFailedAttempts(clientIp);

        AppUserPrincipal principal = AppUserPrincipal.from(user);
        String accessToken = jwtTokenService.generateAccessToken(principal);
        Instant accessExpiresAt = jwtTokenService.accessTokenExpiresAt();

        return new AuthTokenResponse(
                accessToken,
                accessExpiresAt,
                user.getId(),
                user.getRole()
        );
    }

    @Override
    public void logout(String bearerToken) {
        if (bearerToken != null && jwtTokenService.isValid(bearerToken)) {
            revokedTokenService.revoke(bearerToken, jwtTokenService.extractExpiration(bearerToken));
        }
        SecurityContextHolder.clearContext();
    }
}
