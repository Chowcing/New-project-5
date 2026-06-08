package com.example.expense.common.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.statistics.service.StatisticsService;
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
        assertThat(cacheable(TransactionService.class, "recommendTemplates", Long.class, String.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
        assertThat(cacheable(TransactionService.class, "recommendQuickEntry", Long.class, String.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
        assertThat(cacheable(TransactionService.class, "recommendContextTemplates",
                Long.class, String.class, String.class, String.class, java.time.LocalDateTime.class, int.class).cacheNames())
                .containsExactly(CacheNames.RECOMMENDATIONS);
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
}
