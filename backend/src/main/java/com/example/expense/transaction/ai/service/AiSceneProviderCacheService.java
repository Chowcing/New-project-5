package com.example.expense.transaction.ai.service;

import com.example.expense.common.cache.CacheNames;
import com.example.expense.transaction.ai.provider.AiSceneCandidate;
import com.example.expense.transaction.ai.provider.AiSceneProvider;
import com.example.expense.transaction.ai.provider.AiSceneProviderException;
import com.example.expense.transaction.ai.provider.AiSceneProviderRequest;
import com.example.expense.transaction.ai.provider.AiSceneProviderResult;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class AiSceneProviderCacheService {
    private static final String UNAVAILABLE_MESSAGE = "AI 分类服务暂时不可用";

    private final AiSceneRateLimiter rateLimiter;
    private final List<AiSceneProvider> providers;

    public AiSceneProviderCacheService(
            AiSceneRateLimiter rateLimiter,
            List<AiSceneProvider> providers
    ) {
        this.rateLimiter = rateLimiter;
        this.providers = List.copyOf(providers);
    }

    @Cacheable(cacheNames = CacheNames.AI_SCENE, key = "#cacheKey", sync = true)
    public AiSceneProviderResult recommend(
            Long userId,
            String cacheKey,
            String providerName,
            AiSceneProviderRequest request
    ) {
        rateLimiter.checkAllowed(userId);
        AiSceneProvider provider = resolveProvider(providerName);
        AiSceneProviderResult result;
        try {
            result = provider.recommend(request);
        } catch (AiSceneProviderException ex) {
            throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE, ex);
        }
        validateResult(result, request);
        return result;
    }

    private void validateResult(AiSceneProviderResult result, AiSceneProviderRequest request) {
        if (result == null
                || !Double.isFinite(result.confidence())
                || result.confidence() < 0D
                || result.confidence() > 1D
                || !containsToken(request == null ? null : request.categories(), result.categoryToken())) {
            throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE);
        }

        String channel = normalizeChannel(result.channel());
        if (!"ONLINE".equals(channel) && !"OFFLINE".equals(channel)) {
            throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE);
        }
        if ("ONLINE".equals(channel)
                && result.onlinePlatformToken() != null
                && !containsToken(request == null ? null : request.onlinePlatforms(),
                        result.onlinePlatformToken())) {
            throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private boolean containsToken(List<AiSceneCandidate> candidates, String token) {
        if (token == null || candidates == null) {
            return false;
        }
        return candidates.stream()
                .filter(candidate -> candidate != null)
                .anyMatch(candidate -> token.equals(candidate.token()));
    }

    private AiSceneProvider resolveProvider(String providerName) {
        String normalizedProvider = normalizeProviderName(providerName);
        for (AiSceneProvider provider : providers) {
            if (provider != null
                    && normalizedProvider.equals(normalizeProviderName(provider.providerName()))) {
                return provider;
            }
        }
        throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE);
    }

    private String normalizeProviderName(String providerName) {
        return providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
    }
}
