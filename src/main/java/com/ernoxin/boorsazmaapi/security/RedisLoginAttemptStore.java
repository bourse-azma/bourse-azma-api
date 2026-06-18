package com.ernoxin.boorsazmaapi.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisLoginAttemptStore implements LoginAttemptStore {

    private static final String ATTEMPTS_SUFFIX = ":attempts";
    private static final String LOCK_SUFFIX = ":lock";

    private final StringRedisTemplate redisTemplate;

    @Override
    public int getFailedAttempts(String key) {
        String value = redisTemplate.opsForValue().get(key + ATTEMPTS_SUFFIX);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Override
    public void recordFailedAttempt(String key, long attemptWindowSeconds) {
        String attemptsKey = key + ATTEMPTS_SUFFIX;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, Duration.ofSeconds(attemptWindowSeconds));
        }
    }

    @Override
    public void lock(String key, long lockoutDurationSeconds) {
        String lockKey = key + LOCK_SUFFIX;
        redisTemplate.opsForValue().set(lockKey, "1", Duration.ofSeconds(lockoutDurationSeconds));
    }

    @Override
    public boolean isLocked(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key + LOCK_SUFFIX));
    }

    @Override
    public long getRemainingLockoutSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key + LOCK_SUFFIX, TimeUnit.SECONDS);
        if (ttl == null || ttl < 0) {
            return 0;
        }
        return ttl;
    }

    @Override
    public void clear(String key) {
        redisTemplate.delete(key + ATTEMPTS_SUFFIX);
        redisTemplate.delete(key + LOCK_SUFFIX);
    }
}
