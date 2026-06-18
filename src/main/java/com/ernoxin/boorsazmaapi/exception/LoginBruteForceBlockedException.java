package com.ernoxin.boorsazmaapi.exception;

public class LoginBruteForceBlockedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginBruteForceBlockedException(long retryAfterSeconds) {
        super("به دلیل تلاش‌های ناموفق متعدد، ورود موقتا مسدود شده است.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
