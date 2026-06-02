package com.example.expense.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void blocksUsernameAndIpAfterTooManyFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(5, 10, 15, CLOCK);

        for (int index = 0; index < 5; index++) {
            limiter.recordFailure("Demo", "127.0.0.1");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("demo", "127.0.0.1"))
                .isInstanceOf(LoginRateLimitException.class)
                .hasMessage("登录失败次数过多，请稍后再试");
        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter(2, 10, 15, CLOCK);

        limiter.recordFailure("demo", "127.0.0.1");
        limiter.recordSuccess("demo", "127.0.0.1");
        limiter.recordFailure("demo", "127.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void expiredBlockStartsANewFailureWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-27T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(2, 10, 15, clock);

        limiter.recordFailure("demo", "127.0.0.1");
        limiter.recordFailure("demo", "127.0.0.1");
        clock.setInstant(Instant.parse("2026-05-27T00:16:00Z"));
        limiter.recordFailure("demo", "127.0.0.1");

        assertThatCode(() -> limiter.checkAllowed("demo", "127.0.0.1")).doesNotThrowAnyException();
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
