package com.example.expense.transaction.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.common.cache.CacheNames;
import com.example.expense.transaction.ai.provider.AiSceneCandidate;
import com.example.expense.transaction.ai.provider.AiSceneProvider;
import com.example.expense.transaction.ai.provider.AiSceneProviderRequest;
import com.example.expense.transaction.ai.provider.AiSceneProviderResult;
import java.util.List;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AiSceneProviderCacheServiceTest {

    @Test
    void unknownCategoryTokenIsRejectedAndDoesNotPopulateCache() {
        AiSceneRateLimiter rateLimiter = org.mockito.Mockito.mock(AiSceneRateLimiter.class);
        AiSceneProvider provider = org.mockito.Mockito.mock(AiSceneProvider.class);
        when(provider.providerName()).thenReturn("test");
        AiSceneProviderRequest request = request("分类");
        when(provider.recommend(request)).thenReturn(
                new AiSceneProviderResult("category_unknown", "OFFLINE", null, 0.90, "未知分类"),
                new AiSceneProviderResult("category_1", "OFFLINE", null, 0.90, "有效分类"));

        try (AnnotationConfigApplicationContext context = cachingContext(rateLimiter, provider)) {
            AiSceneProviderCacheService service = context.getBean(AiSceneProviderCacheService.class);

            assertThatThrownBy(() -> service.recommend(1001L, "same-key", "test", request))
                    .isInstanceOf(AiSceneUnavailableException.class)
                    .hasMessage("AI 分类服务暂时不可用");

            assertThat(service.recommend(1001L, "same-key", "test", request).reason())
                    .isEqualTo("有效分类");
            verify(provider, times(2)).recommend(request);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProviderResults")
    void invalidProviderResultsAreRejectedAndDoNotPopulateCache(
            String ignoredCase,
            AiSceneProviderRequest request,
            AiSceneProviderResult invalidResult
    ) {
        AiSceneRateLimiter rateLimiter = org.mockito.Mockito.mock(AiSceneRateLimiter.class);
        AiSceneProvider provider = org.mockito.Mockito.mock(AiSceneProvider.class);
        when(provider.providerName()).thenReturn("test");
        when(provider.recommend(request)).thenReturn(
                invalidResult,
                new AiSceneProviderResult("category_1", "OFFLINE", null, 0.25, "有效低置信度"));

        try (AnnotationConfigApplicationContext context = cachingContext(rateLimiter, provider)) {
            AiSceneProviderCacheService service = context.getBean(AiSceneProviderCacheService.class);

            assertThatThrownBy(() -> service.recommend(1001L, "same-key", "test", request))
                    .isInstanceOf(AiSceneUnavailableException.class)
                    .hasMessage("AI 分类服务暂时不可用");

            assertThat(service.recommend(1001L, "same-key", "test", request).reason())
                    .isEqualTo("有效低置信度");
            assertThat(service.recommend(1001L, "same-key", "test", request).reason())
                    .isEqualTo("有效低置信度");
            verify(provider, times(2)).recommend(request);
        }
    }

    @Test
    void offlineResultWithKnownPlatformTokenRemainsCompatibleAndIsCached() {
        AiSceneRateLimiter rateLimiter = org.mockito.Mockito.mock(AiSceneRateLimiter.class);
        AiSceneProvider provider = org.mockito.Mockito.mock(AiSceneProvider.class);
        when(provider.providerName()).thenReturn("test");
        AiSceneProviderRequest request = request("分类");
        when(provider.recommend(request)).thenReturn(
                new AiSceneProviderResult("category_1", "OFFLINE", "platform_1", 0.90, "线下场景"));

        try (AnnotationConfigApplicationContext context = cachingContext(rateLimiter, provider)) {
            AiSceneProviderCacheService service = context.getBean(AiSceneProviderCacheService.class);

            assertThat(service.recommend(1001L, "same-key", "test", request).reason())
                    .isEqualTo("线下场景");
            assertThat(service.recommend(1001L, "same-key", "test", request).reason())
                    .isEqualTo("线下场景");
            verify(provider).recommend(request);
        }
    }

    @Test
    void delayedOldCandidateResultCannotPopulateNewCandidateCacheKey() throws Exception {
        AiSceneRateLimiter rateLimiter = org.mockito.Mockito.mock(AiSceneRateLimiter.class);
        AiSceneProvider provider = org.mockito.Mockito.mock(AiSceneProvider.class);
        when(provider.providerName()).thenReturn("test");
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        AiSceneProviderRequest oldRequest = request("旧分类");
        AiSceneProviderRequest newRequest = request("新分类");
        when(provider.recommend(any())).thenAnswer(invocation -> {
            AiSceneProviderRequest request = invocation.getArgument(0);
            if (request.categories().get(0).name().equals("旧分类")) {
                oldStarted.countDown();
                assertThat(releaseOld.await(5, TimeUnit.SECONDS)).isTrue();
                return new AiSceneProviderResult("category_1", "OFFLINE", null, 0.90, "旧结果");
            }
            return new AiSceneProviderResult("category_1", "OFFLINE", null, 0.91, "新结果");
        });

        try (AnnotationConfigApplicationContext context = cachingContext(rateLimiter, provider)) {
            AiSceneProviderCacheService service = context.getBean(AiSceneProviderCacheService.class);
            CompletableFuture<AiSceneProviderResult> oldFuture = CompletableFuture.supplyAsync(
                    () -> service.recommend(1001L, "old-hashed-key", "test", oldRequest));
            assertThat(oldStarted.await(5, TimeUnit.SECONDS)).isTrue();

            AiSceneProviderResult newResult =
                    service.recommend(1001L, "new-hashed-key", "test", newRequest);
            releaseOld.countDown();
            assertThat(oldFuture.join().reason()).isEqualTo("旧结果");

            assertThat(newResult.reason()).isEqualTo("新结果");
            assertThat(service.recommend(1001L, "new-hashed-key", "test", newRequest).reason())
                    .isEqualTo("新结果");
            verify(provider, times(2)).recommend(any());
        } finally {
            releaseOld.countDown();
        }
    }

    private AiSceneProviderRequest request(String categoryName) {
        return new AiSceneProviderRequest(
                "乐园",
                "EXPENSE",
                List.of(new AiSceneCandidate("category_1", categoryName)),
                List.of(new AiSceneCandidate("platform_1", "线上平台")));
    }

    private static Stream<Arguments> invalidProviderResults() {
        AiSceneProviderRequest request = new AiSceneProviderRequest(
                "乐园",
                "EXPENSE",
                List.of(new AiSceneCandidate("category_1", "分类")),
                List.of(new AiSceneCandidate("platform_1", "线上平台")));
        return Stream.of(
                Arguments.of("未知渠道", request,
                        new AiSceneProviderResult("category_1", "OTHER", null, 0.90, "未知渠道")),
                Arguments.of("非有限置信度", request,
                        new AiSceneProviderResult("category_1", "OFFLINE", null, Double.NaN, "置信度")),
                Arguments.of("越界置信度", request,
                        new AiSceneProviderResult("category_1", "OFFLINE", null, 1.01, "置信度")),
                Arguments.of("未知线上平台", request,
                        new AiSceneProviderResult("category_1", "ONLINE", "platform_unknown", 0.90, "未知平台")));
    }

    private AnnotationConfigApplicationContext cachingContext(
            AiSceneRateLimiter rateLimiter,
            AiSceneProvider provider
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(CacheTestConfiguration.class);
        context.registerBean(AiSceneRateLimiter.class, () -> rateLimiter);
        context.registerBean(AiSceneProvider.class, () -> provider);
        context.registerBean(AiSceneProviderCacheService.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfiguration {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheNames.AI_SCENE);
        }
    }
}
