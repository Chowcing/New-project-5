package com.example.expense.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final String KEY_PREFIX = "auth:login-rate:";

    private final StringRedisTemplate redisTemplate;
    private final int maxFailures;
    private final Duration window;
    private final Duration blockDuration;
    private final Clock clock;

    public LoginRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.login-rate-limit.max-failures:5}") int maxFailures,
            @Value("${app.security.login-rate-limit.window-minutes:10}") long windowMinutes,
            @Value("${app.security.login-rate-limit.block-minutes:15}") long blockMinutes,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = Math.max(maxFailures, 1);
        this.window = Duration.ofMinutes(Math.max(windowMinutes, 1));
        this.blockDuration = Duration.ofMinutes(Math.max(blockMinutes, 1));
        this.clock = clock;
    }

    public void checkAllowed(String username, String clientIp) {
        try {
            String key = key(username, clientIp);
            AttemptState state = AttemptState.parse(redisTemplate.opsForValue().get(key));
            if (state == null) {
                return;
            }
            Instant now = Instant.now(clock);
            if (state.expired(now, window) || state.blockExpired(now)) {
                redisTemplate.delete(key);
                return;
            }
            if (state.blockedUntil() != null && now.isBefore(state.blockedUntil())) {
                throw new LoginRateLimitException("登录失败次数过多，请稍后再试");
            }
        } catch (LoginRateLimitException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public void recordFailure(String username, String clientIp) {
        try {
            String key = key(username, clientIp);
            Instant now = Instant.now(clock);
            AttemptState state = AttemptState.parse(redisTemplate.opsForValue().get(key));
            AttemptState next;
            if (state == null || state.expired(now, window) || state.blockExpired(now)) {
                next = new AttemptState(1, now, null);
            } else {
                int failures = state.failures() + 1;
                Instant blockedUntil = failures >= maxFailures ? now.plus(blockDuration) : state.blockedUntil();
                next = new AttemptState(failures, state.firstFailureAt(), blockedUntil);
            }
            Duration ttl = next.blockedUntil() == null
                    ? Duration.between(now, next.firstFailureAt().plus(window))
                    : Duration.between(now, next.blockedUntil());
            redisTemplate.opsForValue().set(key, next.serialize(), ttl.isNegative() || ttl.isZero() ? window : ttl);
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    public void recordSuccess(String username, String clientIp) {
        try {
            redisTemplate.delete(key(username, clientIp));
        } catch (DataAccessException ex) {
            throw new AuthTemporaryUnavailableException(ex);
        }
    }

    private String key(String username, String clientIp) {
        return KEY_PREFIX + sha256(normalize(username) + "|" + normalize(clientIp));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record AttemptState(int failures, Instant firstFailureAt, Instant blockedUntil) {
        private static AttemptState parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3) {
                return null;
            }
            Instant blockedUntil = parts[2].isBlank() ? null : Instant.parse(parts[2]);
            return new AttemptState(Integer.parseInt(parts[0]), Instant.parse(parts[1]), blockedUntil);
        }

        private String serialize() {
            return failures + "|" + firstFailureAt + "|" + (blockedUntil == null ? "" : blockedUntil);
        }

        private boolean expired(Instant now, Duration window) {
            return blockedUntil == null && firstFailureAt.plus(window).isBefore(now);
        }

        private boolean blockExpired(Instant now) {
            return blockedUntil != null && !now.isBefore(blockedUntil);
        }
    }
}
