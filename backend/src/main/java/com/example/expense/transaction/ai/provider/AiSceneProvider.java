package com.example.expense.transaction.ai.provider;

public interface AiSceneProvider {
    String providerName();

    AiSceneProviderResult recommend(AiSceneProviderRequest request);
}
