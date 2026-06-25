package com.example.expense.common.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

class CacheInvalidationServiceTest {

    @Test
    void evictsUserCacheWithoutRedisKeysCommand() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CacheInvalidationService service = new CacheInvalidationService(redisTemplate);

        service.evictStatisticsAfterCommit(1001L);

        verify(redisTemplate, never()).keys(any());
        verify(redisTemplate).execute(any(RedisCallback.class));
    }
}
