package com.example.expense.transaction.ai.service;

import com.example.expense.transaction.ai.config.AiSceneProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class AiSceneRateLimiter {
    private static final String KEY_PREFIX = "v2:aiSceneRate:user:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], 120000)
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiSceneProperties properties;
    private final Clock clock;

    public AiSceneRateLimiter(
            StringRedisTemplate redisTemplate,
            AiSceneProperties properties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkAllowed(Long userId) {
        try {
            String key = KEY_PREFIX + userId + ":" + clock.instant().getEpochSecond() / 60;
            Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key));
            if (count == null) {
                throw new AiSceneUnavailableException("AI 分类服务暂时不可用");
            }
            if (count > properties.getRateLimitPerMinute()) {
                throw new AiSceneRateLimitException("AI 分类请求过于频繁，请稍后再试");
            }
        } catch (AiSceneRateLimitException | AiSceneUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiSceneUnavailableException("AI 分类服务暂时不可用", ex);
        }
    }
}
