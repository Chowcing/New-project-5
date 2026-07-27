package com.example.expense.transaction.ai.provider;

public record AiSceneProviderResult(
        String categoryToken,
        String channel,
        String onlinePlatformToken,
        double confidence,
        String reason
) {
}
