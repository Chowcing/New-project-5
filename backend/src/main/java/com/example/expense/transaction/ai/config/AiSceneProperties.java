package com.example.expense.transaction.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.ai-scene")
@Getter
@Setter
public class AiSceneProperties {
    private boolean enabled = false;
    private String provider = "disabled";
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double confidenceThreshold = 0.75;
    @Min(1)
    @Max(1000)
    private int rateLimitPerMinute = 20;
    @Valid
    @NotNull
    private DeepSeek deepseek = new DeepSeek();

    @Getter
    @Setter
    public static class DeepSeek {
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        @Min(1)
        @Max(60_000)
        private int timeoutMs = 6000;
    }
}
