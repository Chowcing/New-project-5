package com.example.expense.transaction.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai-scene")
@Getter
@Setter
public class AiSceneProperties {
    private boolean enabled = false;
    private String provider = "disabled";
    private double confidenceThreshold = 0.75;
    private int rateLimitPerMinute = 20;
    private DeepSeek deepseek = new DeepSeek();

    @Getter
    @Setter
    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int timeoutMs = 6000;
    }
}
