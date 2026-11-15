package com.example.expense.transaction.ai.provider;

public interface AiSceneProvider {
    String providerName();

    AiSceneProviderResult recommend(AiSceneProviderRequest request);

    default String suggestItemName(String itemName) {
        throw new AiSceneProviderException("AI 事项优化暂时不可用");
    }
}
