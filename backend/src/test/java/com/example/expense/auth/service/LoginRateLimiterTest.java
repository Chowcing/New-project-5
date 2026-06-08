package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginRateLimiterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void blocksUsernameAndIpAfterTooManyFailures() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        LoginRateLimiter limiter = new LoginRateLimiter(redisTemplate, 5, 10, 15, CLOCK);
        when(valueOperations.get(any())).thenReturn(null, "1|2026-05-27T00:00:00Z|", "2|2026-05-27T00:00:00Z|",
                "3|2026-05-27T00:00:00Z|", "4|2026-05-27T00:00:00Z|");

        for (int index = 0; index < 5; index++) {
            limiter.recordFailure("Demo", "127.0.0.1");
        }

        when(valueOperations.get(any())).thenReturn("5|2026-05-27T00:00:00Z|2026-05-27T00:15:00Z", null);
        assertThatThrownBy(() -> limiter.checkAllowed("demo", "127.0.0.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessage("登录失败次数过多，请稍后再试");
        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsFailures() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        LoginRateLimiter limiter = new LoginRateLimiter(redisTemplate, 2, 10, 15, CLOCK);
        when(valueOperations.get(any())).thenReturn(null, null);

        limiter.recordFailure("demo", "127.0.0.1");
        limiter.recordSuccess("demo", "127.0.0.1");
        limiter.recordFailure("demo", "127.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void expiredBlockStartsANewFailureWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-27T00:00:00Z"));
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        LoginRateLimiter limiter = new LoginRateLimiter(redisTemplate, 2, 10, 15, clock);
        when(valueOperations.get(any())).thenReturn(null, "1|2026-05-27T00:00:00Z|", "2|2026-05-27T00:15:00Z|", null);

        limiter.recordFailure("demo", "127.0.0.1");
        limiter.recordFailure("demo", "127.0.0.1");
        clock.setInstant(Instant.parse("2026-05-27T00:16:00Z"));
        limiter.recordFailure("demo", "127.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void redisFailureMakesAuthenticationTemporarilyUnavailable() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("down"));
        LoginRateLimiter limiter = new LoginRateLimiter(redisTemplate, 2, 10, 15, CLOCK);

        assertThatThrownBy(() -> limiter.checkAllowed("demo", "127.0.0.1"))
                .isInstanceOf(AuthTemporaryUnavailableException.class)
                .hasMessage("认证服务暂时不可用，请稍后再试");
    }

    @Test
    void redisKeyDoesNotExposeUsernameOrIp() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        LoginRateLimiter limiter = new LoginRateLimiter(redisTemplate, 2, 10, 15, CLOCK);
        when(valueOperations.get(any())).thenReturn(null);

        limiter.recordFailure("DemoUser", "192.168.1.10");

        verify(valueOperations).set(eq("auth:login-rate:4c8e0a48f6dd8a751c675bfe6cbf71c765a910b052ead474f949810611d5a0d2"),
                any(String.class), any(Duration.class));
        verify(valueOperations, never()).set(eq("demouser|192.168.1.10"), any(), any());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
