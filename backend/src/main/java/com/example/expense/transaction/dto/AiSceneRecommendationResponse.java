package com.example.expense.transaction.dto;

public record AiSceneRecommendationResponse(
        String status,
        Long categoryId,
        String categoryName,
        String channel,
        Long onlinePlatformId,
        String onlinePlatformName,
        double confidence,
        String reason
) {
    public static AiSceneRecommendationResponse uncertain() {
        return new AiSceneRecommendationResponse(
                "UNCERTAIN",
                null,
                null,
                null,
                null,
                null,
                0D,
                "AI 暂无法确定，请手动选择");
    }
}
