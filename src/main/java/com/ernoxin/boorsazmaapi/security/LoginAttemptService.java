package com.ernoxin.boorsazmaapi.security;

import com.ernoxin.boorsazmaapi.config.LoginBruteForceProperties;
import com.ernoxin.boorsazmaapi.exception.LoginBruteForceBlockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginBruteForceProperties properties;
    private final LoginAttemptStore loginAttemptStore;

    public void ensureLoginAllowed(String identifier) {
        String key = trackingKey(identifier);
        if (loginAttemptStore.isLocked(key)) {
            throw new LoginBruteForceBlockedException(loginAttemptStore.getRemainingLockoutSeconds(key));
        }
    }

    public void recordFailedLogin(String identifier) {
        String key = trackingKey(identifier);
        loginAttemptStore.recordFailedAttempt(key, properties.getAttemptWindowSeconds());
        int attempts = loginAttemptStore.getFailedAttempts(key);
        if (attempts >= properties.getMaxAttempts()) {
            loginAttemptStore.lock(key, properties.getLockoutDurationSeconds());
        }
    }

    public void clearFailedAttempts(String identifier) {
        loginAttemptStore.clear(trackingKey(identifier));
    }

    private String trackingKey(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        return properties.getKeyPrefix() + ":id:" + normalized;
    }
}
