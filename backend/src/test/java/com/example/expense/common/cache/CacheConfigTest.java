package com.example.expense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.expense.auth.entity.AuthChallenge;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class CacheConfigTest {

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
