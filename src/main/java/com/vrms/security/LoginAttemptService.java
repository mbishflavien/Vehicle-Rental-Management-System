package com.vrms.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vrms.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brute-force protection: after too many failed sign-ins for the same email from the same client,
 * further attempts are refused for a cooling-off period.
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration lockout;
    private final Cache<String, AtomicInteger> failures;

    public LoginAttemptService(@Value("${vrms.security.max-login-attempts:5}") int maxAttempts,
                               @Value("${vrms.security.lockout-minutes:15}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
        this.failures = Caffeine.newBuilder().expireAfterWrite(lockout).maximumSize(100_000).build();
    }

    public void checkAllowed(String email, String clientIp) {
        AtomicInteger count = failures.getIfPresent(key(email, clientIp));
        if (count != null && count.get() >= maxAttempts) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Please wait " + lockout.toMinutes() + " minutes and try again.");
        }
    }

    public void recordFailure(String email, String clientIp) {
        failures.get(key(email, clientIp), k -> new AtomicInteger()).incrementAndGet();
    }

    public void recordSuccess(String email, String clientIp) {
        failures.invalidate(key(email, clientIp));
    }

    private static String key(String email, String clientIp) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + clientIp;
    }
}
