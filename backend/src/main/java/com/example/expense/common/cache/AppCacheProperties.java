package com.example.expense.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public class AppCacheProperties {
    private long statisticsTtlMinutes = 15;
    private long recommendationsTtlMinutes = 5;
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

    public long getReferenceDataTtlMinutes() {
        return referenceDataTtlMinutes;
    }

    public void setReferenceDataTtlMinutes(long referenceDataTtlMinutes) {
        this.referenceDataTtlMinutes = referenceDataTtlMinutes;
    }
}
