package com.example.expense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class CacheInvalidationServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void aiSceneEvictionPatternDoesNotCrossUserBoundary() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        Cursor<byte[]> cursor = mock(Cursor.class);
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        CacheInvalidationService service = new CacheInvalidationService(redisTemplate);

        service.evictAiSceneAfterCommit(1L);

        ArgumentCaptor<ScanOptions> optionsCaptor = ArgumentCaptor.forClass(ScanOptions.class);
        verify(keyCommands).scan(optionsCaptor.capture());
        String pattern = optionsCaptor.getValue().getPattern();
        assertThat(pattern).isEqualTo("v2:aiSceneRecommendations::user:1:*");
        assertThat(matchesTrailingWildcard(pattern,
                "v2:aiSceneRecommendations::user:1:ai-scene:hash")).isTrue();
        assertThat(matchesTrailingWildcard(pattern,
                "v2:aiSceneRecommendations::user:10:ai-scene:hash")).isFalse();
        verify(redisTemplate, never()).keys(any());
    }

    @Test
    void evictsUserCacheWithoutRedisKeysCommand() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CacheInvalidationService service = new CacheInvalidationService(redisTemplate);

        service.evictStatisticsAfterCommit(1001L);

        verify(redisTemplate, never()).keys(any());
        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    private boolean matchesTrailingWildcard(String pattern, String key) {
        assertThat(pattern).endsWith("*");
        return key.startsWith(pattern.substring(0, pattern.length() - 1));
    }
}
