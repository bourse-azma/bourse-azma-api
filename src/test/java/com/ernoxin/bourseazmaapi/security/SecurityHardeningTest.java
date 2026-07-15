package com.ernoxin.bourseazmaapi.security;

import com.ernoxin.bourseazmaapi.config.JwtProperties;
import com.ernoxin.bourseazmaapi.config.LoginBruteForceProperties;
import com.ernoxin.bourseazmaapi.exception.LoginBruteForceBlockedException;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.util.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SecurityHardeningTest {

    @Test
    void untrustedClientCannotSpoofItsAddressWithForwardedHeaders() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        MockHttpServletRequest request = request("203.0.113.9", "198.51.100.25");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void trustedProxyChainReturnsTheNearestUntrustedClientHop() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8,192.168.0.0/16");
        MockHttpServletRequest request = request(
                "10.1.2.3",
                "198.51.100.40, 203.0.113.50, 192.168.1.10"
        );

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void malformedRemoteAddressIsNotTrustedOrReflectedBack() {
        ClientIpResolver resolver = new ClientIpResolver("0.0.0.0/0");
        MockHttpServletRequest request = request("not-an-ip", "198.51.100.40");

        assertThat(resolver.resolve(request)).isEqualTo("unknown");
    }

    @Test
    void jwtRoundTripPreservesIdentityVersionAndExpiration() {
        JwtTokenService service = tokenService();
        AppUserPrincipal principal = principal(42L, 7L);

        String token = service.generateAccessToken(principal);

        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUserId(token)).isEqualTo(42L);
        assertThat(service.extractTokenVersion(token)).isEqualTo(7L);
        assertThat(service.extractExpiration(token)).isAfter(Instant.now().plusSeconds(8 * 60));
    }

    @Test
    void tamperedJwtAndTokenFromAnotherSigningInstanceAreRejected() {
        JwtTokenService firstInstance = tokenService();
        JwtTokenService secondInstance = tokenService();
        String token = firstInstance.generateAccessToken(principal(42L, 0L));
        int mutationIndex = token.length() / 2;
        char replacement = token.charAt(mutationIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, mutationIndex) + replacement + token.substring(mutationIndex + 1);

        assertThat(firstInstance.isValid(tampered)).isFalse();
        assertThat(secondInstance.isValid(token)).isFalse();
    }

    @Test
    void concurrentFailedLoginsStillReachTheLockThresholdAtomically() throws Exception {
        LoginBruteForceProperties properties = new LoginBruteForceProperties();
        properties.setMaxAttempts(5);
        properties.setAttemptWindowSeconds(60);
        properties.setLockoutDurationSeconds(300);
        properties.setKeyPrefix("test-login");
        AtomicLoginAttemptStore store = new AtomicLoginAttemptStore();
        LoginAttemptService service = new LoginAttemptService(properties, store);
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            for (int index = 0; index < 32; index++) {
                executor.submit(() -> service.recordFailedLogin(" 203.0.113.8 "));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(store.getFailedAttempts("test-login:ip:203.0.113.8")).isEqualTo(32);
            assertThatThrownBy(() -> service.ensureLoginAllowed("203.0.113.8"))
                    .isInstanceOf(LoginBruteForceBlockedException.class)
                    .satisfies(error -> assertThat(((LoginBruteForceBlockedException) error).getRetryAfterSeconds())
                            .isEqualTo(300));

            service.clearFailedAttempts("203.0.113.8");
            assertThatCode(() -> service.ensureLoginAllowed("203.0.113.8")).doesNotThrowAnyException();
        } finally {
            executor.shutdownNow();
        }
    }

    private JwtTokenService tokenService() {
        JwtProperties properties = new JwtProperties();
        properties.setAccessTokenMinutes(10);
        JwtTokenService service = new JwtTokenService(properties);
        service.init();
        return service;
    }

    private AppUserPrincipal principal(Long id, long tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername("security-user");
        user.setFirstName("Security");
        user.setLastName("User");
        user.setPassword("hash");
        user.setRole(UserRole.USER);
        user.setTokenVersion(tokenVersion);
        return AppUserPrincipal.from(user);
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }

    private static final class AtomicLoginAttemptStore implements LoginAttemptStore {
        private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final Map<String, Long> locks = new ConcurrentHashMap<>();

        @Override
        public int getFailedAttempts(String key) {
            AtomicInteger count = attempts.get(key);
            return count == null ? 0 : count.get();
        }

        @Override
        public void recordFailedAttempt(String key, long attemptWindowSeconds) {
            attempts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        }

        @Override
        public void lock(String key, long lockoutDurationSeconds) {
            locks.put(key, lockoutDurationSeconds);
        }

        @Override
        public boolean isLocked(String key) {
            return locks.containsKey(key);
        }

        @Override
        public long getRemainingLockoutSeconds(String key) {
            return locks.getOrDefault(key, 0L);
        }

        @Override
        public void clear(String key) {
            attempts.remove(key);
            locks.remove(key);
        }
    }
}
