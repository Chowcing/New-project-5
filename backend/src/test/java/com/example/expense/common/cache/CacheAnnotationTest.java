package com.example.expense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.statistics.service.StatisticsService;
import com.example.expense.transaction.ai.provider.AiSceneProviderRequest;
import com.example.expense.transaction.ai.service.AiSceneProviderCacheService;
import com.example.expense.transaction.dto.AiSceneRecommendationRequest;
import com.example.expense.transaction.service.TransactionAiRecommendationService;
import com.example.expense.transaction.service.TransactionService;
import java.time.Year;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

class CacheAnnotationTest {

    @Test
    void statisticsMethodsUseStatisticsCache() throws Exception {
        assertThat(cacheable(StatisticsService.class, "monthly", Long.class, YearMonth.class).cacheNames())
                .containsExactly(CacheNames.STATISTICS);
        assertThat(cacheable(StatisticsService.class, "yearly", Long.class, Year.class).cacheNames())
                .containsExactly(CacheNames.STATISTICS);
    }

    @Test
    void recommendationMethodsUseRecommendationCache() throws Exception {
        Class<?> recommendationService = Class.forName("com.example.expense.transaction.service.TransactionRecommendationService");
        assertThat(TransactionService.class.getMethod("recommendTemplates", Long.class, String.class, int.class)
                .getAnnotation(Cacheable.class)).isNull();
        assertThat(cacheable(recommendationService, "recommendTemplates", Long.class, String.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
        assertThat(cacheable(recommendationService, "recommendQuickEntry", Long.class, String.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
        assertThat(cacheable(recommendationService, "recommendContextTemplates",
                Long.class, String.class, String.class, String.class, java.time.LocalDateTime.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
    }

    @Test
    void aiSceneRecommendationCachesOnlyRawProviderResultsBehindHashedKey() throws Exception {
        assertThat(TransactionAiRecommendationService.class
                .getMethod("recommend", Long.class, AiSceneRecommendationRequest.class)
                .getAnnotation(Cacheable.class)).isNull();
        Cacheable cacheable = cacheable(
                AiSceneProviderCacheService.class,
                "recommend",
                Long.class,
                String.class,
                String.class,
                AiSceneProviderRequest.class);

        assertThat(cacheable.cacheNames()).containsExactly(CacheNames.AI_SCENE);
        assertThat(cacheable.key()).isEqualTo("#cacheKey");
        assertThat(cacheable.sync()).isTrue();
        String policyFingerprint = CacheKeys.aiScenePolicyFingerprint(
                "https://api.deepseek.com",
                "secret-key",
                0.75,
                "ai-scene-prompt-schema-v1");
        String candidateFingerprint = CacheKeys.aiSceneCandidateFingerprint(
                java.util.List.of("category_1", "12", "娱乐", "platform_1", "22", "美团"));
        assertThat(CacheKeys.recommendAiScene(
                1001L,
                "乐园",
                "EXPENSE",
                policyFingerprint,
                candidateFingerprint))
                .startsWith("user:1001:ai-scene:")
                .doesNotContain("乐园")
                .doesNotContain("EXPENSE")
                .doesNotContain("娱乐")
                .doesNotContain("美团")
                .doesNotContain("api.deepseek.com")
                .doesNotContain("secret-key");
    }

    @Test
    void aiScenePolicyFingerprintChangesForProviderModelAndThreshold() {
        String candidates = CacheKeys.aiSceneCandidateFingerprint(java.util.List.of("category_1", "12", "娱乐"));
        String baseline = aiSceneKey(
                "deepseek", "deepseek-v4-flash", 0.75, "ai-scene-prompt-schema-v1", candidates);

        assertThat(java.util.Set.of(
                baseline,
                aiSceneKey("other", "deepseek-v4-flash", 0.75, "ai-scene-prompt-schema-v1", candidates),
                aiSceneKey("deepseek", "deepseek-v5", 0.75, "ai-scene-prompt-schema-v1", candidates),
                aiSceneKey("deepseek", "deepseek-v4-flash", 0.90, "ai-scene-prompt-schema-v1", candidates),
                aiSceneKey("deepseek", "deepseek-v4-flash", 0.75, "ai-scene-prompt-schema-v2", candidates)))
                .hasSize(5);
    }

    @Test
    void referenceDataListMethodsUseReferenceDataCache() throws Exception {
        assertThat(cacheable(CategoryService.class, "list", Long.class, String.class).cacheNames())
                .containsExactly(CacheNames.CATEGORIES);
        assertThat(cacheable(PaymentMethodService.class, "list", Long.class).cacheNames())
                .containsExactly(CacheNames.PAYMENT_METHODS);
        assertThat(cacheable(OnlinePlatformService.class, "list", Long.class).cacheNames())
                .containsExactly(CacheNames.ONLINE_PLATFORMS);
    }

    private Cacheable cacheable(Class<?> type, String methodName, Class<?>... parameterTypes) throws Exception {
        Cacheable cacheable = type.getMethod(methodName, parameterTypes).getAnnotation(Cacheable.class);
        assertThat(cacheable).as(type.getSimpleName() + "." + methodName + " @Cacheable").isNotNull();
        return cacheable;
    }

    private String aiSceneKey(
            String provider,
            String model,
            double threshold,
            String promptSchemaVersion,
            String candidateFingerprint
    ) {
        return CacheKeys.recommendAiScene(
                1001L,
                "乐园",
                "EXPENSE",
                CacheKeys.aiScenePolicyFingerprint(provider, model, threshold, promptSchemaVersion),
                candidateFingerprint);
    }
}
