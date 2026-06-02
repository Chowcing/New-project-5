package com.example.expense.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int CLEANUP_INTERVAL = 256;

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;
    private int requestCount;

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.security.login-rate-limit.window-minutes:10}") long windowMinutes,
            @Value("${app.security.login-rate-limit.block-minutes:15}") long blockMinutes,
            Clock clock
    ) {
        this.maxFailures = Math.max(maxFailures, 1);
        this.window = Duration.ofMinutes(Math.max(windowMinutes, 1));
        this.blockDuration = Duration.ofMinutes(Math.max(blockMinutes, 1));
        this.clock = clock;
    }

    public void checkAllowed(String username, String clientIp) {
        cleanupOccasionally();
        String key = key(username, clientIp);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }
        Instant now = Instant.now(clock);
        if (state.expired(now, window) || state.blockExpired(now)) {
            attempts.remove(key, state);
            return;
        }
        if (state.blockedUntil() != null && now.isBefore(state.blockedUntil())) {
            throw new LoginRateLimitException("登录失败次数过多，请稍后再试");
        }
    }

    public void recordFailure(String username, String clientIp) {
        cleanupOccasionally();
        String key = key(username, clientIp);
        Instant now = Instant.now(clock);
        attempts.compute(key, (ignored, state) -> {
            if (state == null || state.expired(now, window) || state.blockExpired(now)) {
                return new AttemptState(1, now, null);
            }
            int failures = state.failures() + 1;
            Instant blockedUntil = failures >= maxFailures ? now.plus(blockDuration) : state.blockedUntil();
            return new AttemptState(failures, state.firstFailureAt(), blockedUntil);
        });
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    private String key(String username, String clientIp) {
        return normalize(username) + "|" + normalize(clientIp);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private synchronized void cleanupOccasionally() {
        requestCount++;
        if (requestCount % CLEANUP_INTERVAL != 0) {
            return;
        }
        Instant now = Instant.now(clock);
        attempts.entrySet().removeIf(entry -> entry.getValue().expired(now, window) || entry.getValue().blockExpired(now));
    }

    private record AttemptState(int failures, Instant firstFailureAt, Instant blockedUntil) {
        private boolean expired(Instant now, Duration window) {
            return blockedUntil == null && firstFailureAt.plus(window).isBefore(now);
        }

        private boolean blockExpired(Instant now) {
            return blockedUntil != null && !now.isBefore(blockedUntil);
        }
    }
}
