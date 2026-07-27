package com.example.expense.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expense.common.security.JwtProperties;
import com.example.expense.transaction.ai.config.AiSceneProperties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
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

    @Test
    void rejectsMissingDeepSeekApiKeyWhenAiSceneIsEnabledInProd() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "prod-redis-password")
                .withProperty("app.ai-scene.enabled", "true")
                .withProperty("app.ai-scene.provider", "deepseek")
                .withProperty("app.ai-scene.deepseek.api-key", "");
        environment.setActiveProfiles("prod");
        ProductionStartupValidator validator = new ProductionStartupValidator(
                environment,
                properties("prod-secret-value-at-least-32-bytes"));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void rejectsMissingDeepSeekApiKeyCaseInsensitively() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "prod-redis-password")
                .withProperty("app.ai-scene.enabled", "true")
                .withProperty("app.ai-scene.provider", "DeEpSeEk")
                .withProperty("app.ai-scene.deepseek.api-key", " ");
        environment.setActiveProfiles("prod");
        ProductionStartupValidator validator = new ProductionStartupValidator(
                environment,
                properties("prod-secret-value-at-least-32-bytes"));

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
    }

    @Test
    void allowsMissingDeepSeekApiKeyWhenAiSceneIsDisabledInProd() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "prod-redis-password")
                .withProperty("app.ai-scene.enabled", "false")
                .withProperty("app.ai-scene.provider", "disabled");
        environment.setActiveProfiles("prod");
        ProductionStartupValidator validator = new ProductionStartupValidator(
                environment,
                properties("prod-secret-value-at-least-32-bytes"));

        assertThatCode(() -> validator.run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsEnabledDeepSeekWithNonblankKeyInProd() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("app.ai-scene.enabled", "true")
                .withProperty("app.ai-scene.provider", "deepseek")
                .withProperty("app.ai-scene.deepseek.api-key", "configured-key");
        ProductionStartupValidator validator = new ProductionStartupValidator(
                environment,
                properties("prod-secret-value-at-least-32-bytes"));

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    void allowsEnabledNonDeepSeekProviderWithoutDeepSeekKeyInProd() {
        MockEnvironment environment = validProdEnvironment()
                .withProperty("app.ai-scene.enabled", "true")
                .withProperty("app.ai-scene.provider", "test-provider")
                .withProperty("app.ai-scene.deepseek.api-key", "");
        ProductionStartupValidator validator = new ProductionStartupValidator(
                environment,
                properties("prod-secret-value-at-least-32-bytes"));

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @TestFactory
    Stream<DynamicTest> rejectsInvalidAiSceneConfigurationInEveryProfile() {
        return Stream.of(
                        "app.ai-scene.confidence-threshold=-0.01",
                        "app.ai-scene.confidence-threshold=1.01",
                        "app.ai-scene.confidence-threshold=NaN",
                        "app.ai-scene.confidence-threshold=Infinity",
                        "app.ai-scene.rate-limit-per-minute=-1",
                        "app.ai-scene.rate-limit-per-minute=0",
                        "app.ai-scene.rate-limit-per-minute=1001",
                        "app.ai-scene.deepseek.timeout-ms=-1",
                        "app.ai-scene.deepseek.timeout-ms=0",
                        "app.ai-scene.deepseek.timeout-ms=60001")
                .map(property -> DynamicTest.dynamicTest(property, () ->
                        aiSceneContextRunner(property).run(context ->
                                org.assertj.core.api.Assertions.assertThat(context)
                                        .hasFailed())));
    }

    @Test
    void allowsAiSceneConfigurationBoundaryValues() {
        aiSceneContextRunner(
                "app.ai-scene.confidence-threshold=0",
                "app.ai-scene.rate-limit-per-minute=1",
                "app.ai-scene.deepseek.timeout-ms=1")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context)
                        .hasNotFailed());

        new ApplicationContextRunner()
                .withUserConfiguration(AiScenePropertiesTestConfiguration.class)
                .withPropertyValues(
                        "app.ai-scene.confidence-threshold=1",
                        "app.ai-scene.rate-limit-per-minute=1000",
                        "app.ai-scene.deepseek.timeout-ms=60000")
                .run(context -> org.assertj.core.api.Assertions.assertThat(context)
                        .hasNotFailed());
    }

    private ApplicationContextRunner aiSceneContextRunner(String property) {
        return aiSceneContextRunner(new String[]{property});
    }

    private ApplicationContextRunner aiSceneContextRunner(String... properties) {
        return new ApplicationContextRunner()
                .withUserConfiguration(AiScenePropertiesTestConfiguration.class)
                .withPropertyValues(properties);
    }

    private MockEnvironment validProdEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.password", "prod-redis-password");
        environment.setActiveProfiles("prod");
        return environment;
    }

    private JwtProperties properties(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setAccessTokenMinutes(30);
        properties.setRefreshTokenDays(14);
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiSceneProperties.class)
    static class AiScenePropertiesTestConfiguration {
    }
}
