package com.example.expense.transaction.ai.provider;

import org.springframework.stereotype.Component;

@Component
public class DisabledAiSceneProvider implements AiSceneProvider {
    private static final String SAFE_FAILURE_MESSAGE = "AI 分类服务暂时不可用";

    @Override
    public String providerName() {
        return "disabled";
    }

    @Override
    public AiSceneProviderResult recommend(AiSceneProviderRequest request) {
        throw new AiSceneProviderException(SAFE_FAILURE_MESSAGE);
    }
}
