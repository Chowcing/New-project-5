package com.example.expense.common.config;

import com.example.expense.common.security.JwtProperties;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionStartupValidator implements ApplicationRunner {
    private final Environment environment;
    private final JwtProperties jwtProperties;

    public ProductionStartupValidator(Environment environment, JwtProperties jwtProperties) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProdProfile()) {
            return;
        }
        String secret = jwtProperties.getSecret();
        String normalized = secret.toLowerCase(Locale.ROOT);
        if (normalized.contains("please-change") || normalized.contains("change-me")) {
            throw new IllegalStateException("生产环境 JWT_SECRET 不能使用占位值，请配置随机密钥");
        }
        String redisPassword = environment.getProperty("spring.data.redis.password", "");
        String normalizedRedisPassword = redisPassword.toLowerCase(Locale.ROOT);
        if (normalizedRedisPassword.isBlank()
                || normalizedRedisPassword.contains("please-change")
                || normalizedRedisPassword.contains("change-me")) {
            throw new IllegalStateException("生产环境 REDIS_PASSWORD 不能使用空值或占位值，请配置随机密码");
        }
        boolean aiSceneEnabled = environment.getProperty("app.ai-scene.enabled", Boolean.class, false);
        String aiSceneProvider = environment.getProperty("app.ai-scene.provider", "disabled");
        String deepSeekApiKey = environment.getProperty("app.ai-scene.deepseek.api-key", "");
        if (aiSceneEnabled
                && "deepseek".equalsIgnoreCase(aiSceneProvider.trim())
                && deepSeekApiKey.isBlank()) {
            throw new IllegalStateException("生产环境启用 DeepSeek AI 分类时必须配置 DEEPSEEK_API_KEY");
        }
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
