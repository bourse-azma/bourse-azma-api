package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.config.LoginBruteForceProperties;
import com.ernoxin.bourseazmaapi.exception.LoginBruteForceBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginBruteForceProperties properties;
    private final LoginAttemptStore loginAttemptStore;

    public void ensureLoginAllowed(String clientIp) {
        String key = trackingKey(clientIp);
        if (loginAttemptStore.isLocked(key)) {
            throw new LoginBruteForceBlockedException(loginAttemptStore.getRemainingLockoutSeconds(key));
        }
    }

    public void recordFailedLogin(String clientIp) {
        String key = trackingKey(clientIp);
        loginAttemptStore.recordFailedAttempt(key, properties.getAttemptWindowSeconds());
        int attempts = loginAttemptStore.getFailedAttempts(key);
        if (attempts >= properties.getMaxAttempts()) {
            loginAttemptStore.lock(key, properties.getLockoutDurationSeconds());
        }
    }

    public void clearFailedAttempts(String clientIp) {
        loginAttemptStore.clear(trackingKey(clientIp));
    }

    private String trackingKey(String clientIp) {
        String normalized = clientIp == null || clientIp.isBlank()
                ? "unknown"
                : clientIp.trim().toLowerCase(Locale.ROOT);
        return properties.getKeyPrefix() + ":ip:" + normalized;
    }
}
