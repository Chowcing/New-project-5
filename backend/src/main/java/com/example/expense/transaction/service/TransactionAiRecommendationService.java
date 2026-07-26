package com.example.expense.transaction.service;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.ai.config.AiSceneProperties;
import com.example.expense.transaction.ai.provider.AiSceneCandidate;
import com.example.expense.transaction.ai.provider.AiSceneProvider;
import com.example.expense.transaction.ai.provider.AiSceneProviderException;
import com.example.expense.transaction.ai.provider.AiSceneProviderRequest;
import com.example.expense.transaction.ai.provider.AiSceneProviderResult;
import com.example.expense.transaction.ai.service.AiSceneRateLimiter;
import com.example.expense.transaction.ai.service.AiSceneUnavailableException;
import com.example.expense.transaction.dto.AiSceneAvailabilityResponse;
import com.example.expense.transaction.dto.AiSceneRecommendationRequest;
import com.example.expense.transaction.dto.AiSceneRecommendationResponse;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TransactionAiRecommendationService {
    private static final String UNAVAILABLE_MESSAGE = "AI 分类服务暂时不可用";
    private static final int REASON_MAX_LENGTH = 80;

    private final CategoryService categoryService;
    private final OnlinePlatformService onlinePlatformService;
    private final AiSceneRateLimiter rateLimiter;
    private final AiSceneProperties properties;
    private final List<AiSceneProvider> providers;

    public TransactionAiRecommendationService(
            CategoryService categoryService,
            OnlinePlatformService onlinePlatformService,
            AiSceneRateLimiter rateLimiter,
            AiSceneProperties properties,
            List<AiSceneProvider> providers
    ) {
        this.categoryService = categoryService;
        this.onlinePlatformService = onlinePlatformService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.providers = List.copyOf(providers);
    }

    @Cacheable(
            cacheNames = CacheNames.AI_SCENE,
            key = "T(com.example.expense.common.cache.CacheKeys)"
                    + ".recommendAiScene(#userId, #request.itemName(), #request.type())",
            sync = true)
    public AiSceneRecommendationResponse recommend(Long userId, AiSceneRecommendationRequest request) {
        String selectedProvider = normalizeProviderName(properties.getProvider());
        if (!properties.isEnabled() || selectedProvider.isEmpty() || "disabled".equals(selectedProvider)) {
            throw unavailable();
        }

        rateLimiter.checkAllowed(userId);

        String itemName = normalizeItemName(request.itemName());
        String type = normalizeType(request.type());
        Map<String, Category> categories = ownedCategories(userId, type);
        Map<String, OnlinePlatform> platforms = ownedPlatforms(userId);
        AiSceneProvider provider = resolveProvider(selectedProvider);
        AiSceneProviderRequest providerRequest = new AiSceneProviderRequest(
                itemName,
                type,
                candidates(categories),
                candidates(platforms));

        AiSceneProviderResult result;
        try {
            result = provider.recommend(providerRequest);
        } catch (AiSceneProviderException ex) {
            throw new AiSceneUnavailableException(UNAVAILABLE_MESSAGE, ex);
        }

        if (result == null
                || !Double.isFinite(result.confidence())
                || result.confidence() < 0D
                || result.confidence() > 1D) {
            throw unavailable();
        }
        if (result.confidence() < properties.getConfidenceThreshold()) {
            return AiSceneRecommendationResponse.uncertain();
        }

        Category category = categories.get(result.categoryToken());
        if (category == null) {
            throw unavailable();
        }
        String channel = normalizeChannel(result.channel());
        if (!"ONLINE".equals(channel) && !"OFFLINE".equals(channel)) {
            throw unavailable();
        }

        OnlinePlatform platform = null;
        if ("ONLINE".equals(channel) && result.onlinePlatformToken() != null) {
            platform = platforms.get(result.onlinePlatformToken());
            if (platform == null) {
                throw unavailable();
            }
        }

        return new AiSceneRecommendationResponse(
                "SUGGESTED",
                category.getId(),
                category.getName(),
                channel,
                platform == null ? null : platform.getId(),
                platform == null ? null : platform.getName(),
                result.confidence(),
                sanitizeReason(result.reason()));
    }

    public AiSceneAvailabilityResponse availability() {
        String selectedProvider = normalizeProviderName(properties.getProvider());
        return new AiSceneAvailabilityResponse(
                properties.isEnabled()
                        && !selectedProvider.isEmpty()
                        && !"disabled".equals(selectedProvider));
    }

    private Map<String, Category> ownedCategories(Long userId, String type) {
        Map<String, Category> result = new LinkedHashMap<>();
        List<Category> categories = categoryService.list(userId, type);
        if (categories == null) {
            return result;
        }
        int index = 1;
        for (Category category : categories) {
            if (category != null
                    && Objects.equals(userId, category.getUserId())
                    && Objects.equals(type, normalizeType(category.getType()))
                    && category.getId() != null
                    && category.getName() != null
                    && !category.getName().isBlank()) {
                result.put("category_" + index++, category);
            }
        }
        return result;
    }

    private Map<String, OnlinePlatform> ownedPlatforms(Long userId) {
        Map<String, OnlinePlatform> result = new LinkedHashMap<>();
        List<OnlinePlatform> platforms = onlinePlatformService.list(userId);
        if (platforms == null) {
            return result;
        }
        int index = 1;
        for (OnlinePlatform platform : platforms) {
            if (platform != null
                    && Objects.equals(userId, platform.getUserId())
                    && platform.getId() != null
                    && platform.getName() != null
                    && !platform.getName().isBlank()) {
                result.put("platform_" + index++, platform);
            }
        }
        return result;
    }

    private <T> List<AiSceneCandidate> candidates(Map<String, T> owned) {
        List<AiSceneCandidate> candidates = new ArrayList<>(owned.size());
        owned.forEach((token, value) -> candidates.add(new AiSceneCandidate(token, candidateName(value))));
        return List.copyOf(candidates);
    }

    private String candidateName(Object candidate) {
        if (candidate instanceof Category category) {
            return category.getName();
        }
        return ((OnlinePlatform) candidate).getName();
    }

    private AiSceneProvider resolveProvider(String selectedProvider) {
        for (AiSceneProvider provider : providers) {
            if (provider != null
                    && selectedProvider.equals(normalizeProviderName(provider.providerName()))) {
                return provider;
            }
        }
        throw unavailable();
    }

    private String normalizeItemName(String itemName) {
        return Normalizer.normalize(itemName == null ? "" : itemName, Normalizer.Form.NFKC).trim();
    }

    private String normalizeType(String type) {
        return Normalizer.normalize(type == null ? "" : type, Normalizer.Form.NFKC)
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeChannel(String channel) {
        return channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeProviderName(String providerName) {
        return providerName == null ? "" : providerName.trim().toLowerCase(Locale.ROOT);
    }

    private String sanitizeReason(String reason) {
        String sanitized = reason == null ? "" : reason.replaceAll("\\p{Cc}", "").trim();
        if (sanitized.codePointCount(0, sanitized.length()) <= REASON_MAX_LENGTH) {
            return sanitized;
        }
        StringBuilder limited = new StringBuilder();
        sanitized.codePoints().limit(REASON_MAX_LENGTH).forEach(limited::appendCodePoint);
        return limited.toString();
    }

    private AiSceneUnavailableException unavailable() {
        return new AiSceneUnavailableException(UNAVAILABLE_MESSAGE);
    }
}
