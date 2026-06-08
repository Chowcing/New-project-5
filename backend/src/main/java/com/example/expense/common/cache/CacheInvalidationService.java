package com.example.expense.common.cache;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CacheInvalidationService {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final StringRedisTemplate redisTemplate;

    public CacheInvalidationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void evictStatisticsAfterCommit(Long userId) {
        afterCommit(() -> evictUserCaches(userId, CacheNames.STATISTICS));
    }

    public void evictRecommendationsAfterCommit(Long userId) {
        afterCommit(() -> evictUserCaches(userId, CacheNames.RECOMMENDATIONS));
    }

    public void evictCategoriesAfterCommit(Long userId) {
        afterCommit(() -> evictUserCaches(userId, CacheNames.CATEGORIES));
    }

    public void evictPaymentMethodsAfterCommit(Long userId) {
        afterCommit(() -> evictUserCaches(userId, CacheNames.PAYMENT_METHODS));
    }

    public void evictOnlinePlatformsAfterCommit(Long userId) {
        afterCommit(() -> evictUserCaches(userId, CacheNames.ONLINE_PLATFORMS));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void evictUserCaches(Long userId, String cacheName) {
        if (userId == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(cacheName + "::user:" + userId + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("清理用户缓存失败 cache={} userId={}", cacheName, userId);
        }
    }
}
