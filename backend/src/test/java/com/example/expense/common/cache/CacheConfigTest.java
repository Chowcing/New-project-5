package com.example.expense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.expense.auth.entity.AuthChallenge;
import com.example.expense.statistics.dto.MonthlyStatisticsResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class CacheConfigTest {

    @Test
    void cacheValueSerializerDeserializesCachedDtoWithOriginalType() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer(objectMapper);
        MonthlyStatisticsResponse response = new MonthlyStatisticsResponse(
                "2026-06",
                new BigDecimal("12.50"),
                new BigDecimal("100.00"),
                new BigDecimal("87.50"),
                2L,
                1L,
                1L,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        byte[] encoded = serializer.serialize(response);
        Object decoded = serializer.deserialize(encoded);

        assertThat(decoded).isInstanceOf(MonthlyStatisticsResponse.class);
        assertThat(((MonthlyStatisticsResponse) decoded).month()).isEqualTo("2026-06");
    }

    @Test
    void authChallengeRedisTemplateDeserializesAuthChallenge() {
        CacheConfig config = new CacheConfig();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisTemplate<String, AuthChallenge> template = config.authChallengeRedisTemplate(
                mock(RedisConnectionFactory.class),
                objectMapper);
        RedisSerializer<?> serializer = template.getValueSerializer();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setChallengeId("challenge-1");
        challenge.setUserId(1001L);
        challenge.setPurpose("LOGIN");
        challenge.setEmail("demo@example.com");
        challenge.setCodeHash("hash");
        challenge.setAttemptCount(0);
        challenge.setSentAt(LocalDateTime.of(2026, 6, 8, 10, 49));
        challenge.setExpiresAt(LocalDateTime.of(2026, 6, 8, 10, 59));

        byte[] encoded = ((RedisSerializer<AuthChallenge>) serializer).serialize(challenge);
        Object decoded = serializer.deserialize(encoded);

        assertThat(decoded).isInstanceOf(AuthChallenge.class);
        AuthChallenge decodedChallenge = (AuthChallenge) decoded;
        assertThat(decodedChallenge.getChallengeId()).isEqualTo("challenge-1");
        assertThat(decodedChallenge.getUserId()).isEqualTo(1001L);
    }
}
