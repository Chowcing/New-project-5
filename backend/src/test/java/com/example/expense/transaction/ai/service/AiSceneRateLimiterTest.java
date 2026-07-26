package com.example.expense.transaction.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.transaction.ai.config.AiSceneProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class AiSceneRateLimiterTest {
    private static final String RATE_KEY = "v2:aiSceneRate:user:1001:29750400";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-26T00:00:00Z"),
            ZoneOffset.UTC);

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AiSceneRateLimiter limiter;

    @BeforeEach
    void setUp() {
        AiSceneProperties properties = new AiSceneProperties();
        properties.setRateLimitPerMinute(20);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        limiter = new AiSceneRateLimiter(redisTemplate, properties, CLOCK);
    }

    @Test
    void firstIncrementAppliesTwoMinuteExpiry() {
        when(valueOperations.increment(RATE_KEY)).thenReturn(1L);

        limiter.checkAllowed(1001L);

        verify(redisTemplate).expire(RATE_KEY, Duration.ofMinutes(2));
    }

    @Test
    void allowsRequestAtPerUserMinuteLimit() {
        when(valueOperations.increment(RATE_KEY)).thenReturn(20L);

        assertThatCode(() -> limiter.checkAllowed(1001L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestWhenPerUserMinuteLimitIsExceeded() {
        when(valueOperations.increment(RATE_KEY)).thenReturn(21L);

        assertThatThrownBy(() -> limiter.checkAllowed(1001L))
                .isInstanceOf(AiSceneRateLimitException.class)
                .hasMessage("AI 分类请求过于频繁，请稍后再试");
    }

    @Test
    void nullRedisCountMakesAiSceneUnavailable() {
        when(valueOperations.increment(RATE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> limiter.checkAllowed(1001L))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用");
    }

    @Test
    void redisFailureMakesAiSceneUnavailableWithoutMemoryFallback() {
        when(valueOperations.increment(RATE_KEY))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> limiter.checkAllowed(1001L))
                .isInstanceOf(AiSceneUnavailableException.class)
                .hasMessage("AI 分类服务暂时不可用");
    }
}
