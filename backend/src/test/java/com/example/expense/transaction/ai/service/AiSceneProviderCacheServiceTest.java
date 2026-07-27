package com.example.expense.transaction.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AiSceneProviderCacheServiceTest {

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
                List.of());
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
