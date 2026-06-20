package com.ernoxin.bourseazmaapi.security;

public interface LoginAttemptStore {

    int getFailedAttempts(String key);

    void recordFailedAttempt(String key, long attemptWindowSeconds);

    void lock(String key, long lockoutDurationSeconds);

    boolean isLocked(String key);

    long getRemainingLockoutSeconds(String key);

    void clear(String key);
}
