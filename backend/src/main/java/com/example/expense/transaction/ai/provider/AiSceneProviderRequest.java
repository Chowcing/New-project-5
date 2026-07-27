package com.example.expense.transaction.ai.provider;

import java.util.List;

public record AiSceneProviderRequest(
        String itemName,
        String type,
        List<AiSceneCandidate> categories,
        List<AiSceneCandidate> onlinePlatforms
) {
}
