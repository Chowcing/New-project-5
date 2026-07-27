package com.example.expense.common.cache;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {
    private long statisticsTtlMinutes = 15;
    private long recommendationsTtlMinutes = 5;
    @Min(1)
    @Max(720)
    private long aiSceneTtlHours = 24;
    private long referenceDataTtlMinutes = 30;

    public long getStatisticsTtlMinutes() {
        return statisticsTtlMinutes;
    }

    public void setStatisticsTtlMinutes(long statisticsTtlMinutes) {
        this.statisticsTtlMinutes = statisticsTtlMinutes;
    }

    public long getRecommendationsTtlMinutes() {
        return recommendationsTtlMinutes;
    }

    public void setRecommendationsTtlMinutes(long recommendationsTtlMinutes) {
        this.recommendationsTtlMinutes = recommendationsTtlMinutes;
    }

    public long getAiSceneTtlHours() {
        return aiSceneTtlHours;
    }

    public void setAiSceneTtlHours(long aiSceneTtlHours) {
        this.aiSceneTtlHours = aiSceneTtlHours;
    }

    public long getReferenceDataTtlMinutes() {
        return referenceDataTtlMinutes;
    }

    public void setReferenceDataTtlMinutes(long referenceDataTtlMinutes) {
        this.referenceDataTtlMinutes = referenceDataTtlMinutes;
    }
}
