package com.example.expense.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expense.common.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionStartupValidatorTest {

    @Test
    void rejectsPlaceholderJwtSecretInProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        JwtProperties properties = properties("please-change-this-prod-secret-32-bytes-min");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment, properties);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void allowsPlaceholderJwtSecretOutsideProd() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        JwtProperties properties = properties("please-change-this-dev-secret-32-bytes-min");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment, properties);

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlaceholderRedisPasswordInProd() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "change-me-redis");
        environment.setActiveProfiles("prod");
        JwtProperties properties = properties("prod-secret-value-at-least-32-bytes");

        ProductionStartupValidator validator = new ProductionStartupValidator(environment, properties);

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD");
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenMinutes(30);
        properties.setRefreshTokenDays(14);
        return properties;
    }
}
