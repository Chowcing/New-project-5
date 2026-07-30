package com.example.expense.transaction.service;

import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.transaction.dto.ExpiredTrashCandidate;
import com.example.expense.transaction.dto.TrashCleanupResult;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TransactionTrashCleanupService {
    private static final Logger log =
            LoggerFactory.getLogger(TransactionTrashCleanupService.class);
    private static final int BATCH_SIZE = 200;

    private final TransactionMapper transactionMapper;
    private final TransactionService transactionService;
    private final CacheInvalidationService cacheInvalidationService;
    private final Clock clock;

    public TransactionTrashCleanupService(
            TransactionMapper transactionMapper,
            TransactionService transactionService,
            CacheInvalidationService cacheInvalidationService,
            Clock clock
    ) {
        this.transactionMapper = transactionMapper;
        this.transactionService = transactionService;
        this.cacheInvalidationService = cacheInvalidationService;
        this.clock = clock;
    }

    public TrashCleanupResult cleanupExpired() {
        LocalDateTime runAt =
                LocalDateTime.ofInstant(clock.instant(), clock.getZone());
        long afterId = 0L;
        int deleted = 0;
        int failed = 0;

        while (true) {
            List<ExpiredTrashCandidate> batch =
                    transactionMapper.selectExpiredTrashCandidates(
                            runAt, afterId, BATCH_SIZE);
            if (batch.isEmpty()) {
                return new TrashCleanupResult(deleted, failed);
            }
            Set<Long> affectedUserIds = new LinkedHashSet<>();
            for (ExpiredTrashCandidate candidate : batch) {
                afterId = candidate.id();
                try {
                    if (transactionService.autoDeleteExpired(
                            candidate.userId(), candidate.id(), runAt)) {
                        deleted++;
                        affectedUserIds.add(candidate.userId());
                    }
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
            for (Long userId : affectedUserIds) {
                evictCaches(userId);
            }
        }
    }

    private void evictCaches(Long userId) {
        try {
            cacheInvalidationService.evictStatisticsAfterCommit(userId);
        } catch (RuntimeException ex) {
            log.warn("回收站自动清理缓存失效失败 cache=statistics", ex);
        }
        try {
            cacheInvalidationService.evictRecommendationsAfterCommit(userId);
        } catch (RuntimeException ex) {
            log.warn("回收站自动清理缓存失效失败 cache=recommendations", ex);
        }
    }
}
