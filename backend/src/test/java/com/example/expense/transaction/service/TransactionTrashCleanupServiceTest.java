package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.transaction.dto.ExpiredTrashCandidate;
import com.example.expense.transaction.dto.TrashCleanupResult;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionTrashCleanupServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T19:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime RUN_AT =
            LocalDateTime.of(2026, 7, 30, 3, 0);

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private CacheInvalidationService cacheInvalidationService;

    private TransactionTrashCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new TransactionTrashCleanupService(
                transactionMapper,
                transactionService,
                cacheInvalidationService,
                CLOCK);
    }

    @Test
    void cleanupExpiredUsesIdCursorAndContinuesAfterIndividualFailure() {
        ExpiredTrashCandidate first = new ExpiredTrashCandidate(11L, 1001L);
        ExpiredTrashCandidate sameUser = new ExpiredTrashCandidate(12L, 1001L);
        ExpiredTrashCandidate failed = new ExpiredTrashCandidate(13L, 1002L);
        ExpiredTrashCandidate noLongerExpired =
                new ExpiredTrashCandidate(21L, 1003L);
        ExpiredTrashCandidate nextBatch =
                new ExpiredTrashCandidate(31L, 1004L);
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 0L, 200))
                .thenReturn(List.of(first, sameUser, failed, noLongerExpired));
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 21L, 200))
                .thenReturn(List.of(nextBatch));
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 31L, 200))
                .thenReturn(List.of());
        when(transactionService.autoDeleteExpired(1001L, 11L, RUN_AT))
                .thenReturn(true);
        when(transactionService.autoDeleteExpired(1001L, 12L, RUN_AT))
                .thenReturn(true);
        when(transactionService.autoDeleteExpired(1002L, 13L, RUN_AT))
                .thenThrow(new IllegalStateException("单条清理失败"));
        when(transactionService.autoDeleteExpired(1003L, 21L, RUN_AT))
                .thenReturn(false);
        when(transactionService.autoDeleteExpired(1004L, 31L, RUN_AT))
                .thenReturn(true);

        TrashCleanupResult result = cleanupService.cleanupExpired();

        assertThat(result).isEqualTo(new TrashCleanupResult(3, 1));
        InOrder queryOrder = inOrder(transactionMapper);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 0L, 200);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 21L, 200);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 31L, 200);
        verify(transactionService).autoDeleteExpired(1001L, 11L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1001L, 12L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1002L, 13L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1003L, 21L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1004L, 31L, RUN_AT);
        verify(cacheInvalidationService, times(1))
                .evictStatisticsAfterCommit(1001L);
        verify(cacheInvalidationService, times(1))
                .evictRecommendationsAfterCommit(1001L);
        verify(cacheInvalidationService, times(1))
                .evictStatisticsAfterCommit(1004L);
        verify(cacheInvalidationService, times(1))
                .evictRecommendationsAfterCommit(1004L);
        verify(cacheInvalidationService, never())
                .evictStatisticsAfterCommit(1002L);
        verify(cacheInvalidationService, never())
                .evictRecommendationsAfterCommit(1002L);
        verify(cacheInvalidationService, never())
                .evictStatisticsAfterCommit(1003L);
        verify(cacheInvalidationService, never())
                .evictRecommendationsAfterCommit(1003L);
    }

    @Test
    void cacheFailureDoesNotChangeCommittedCleanupResult() {
        ExpiredTrashCandidate candidate =
                new ExpiredTrashCandidate(11L, 1001L);
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 0L, 200))
                .thenReturn(List.of(candidate));
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 11L, 200))
                .thenReturn(List.of());
        when(transactionService.autoDeleteExpired(1001L, 11L, RUN_AT))
                .thenReturn(true);
        doThrow(new IllegalStateException("缓存不可用"))
                .when(cacheInvalidationService)
                .evictStatisticsAfterCommit(1001L);

        TrashCleanupResult result = cleanupService.cleanupExpired();

        assertThat(result).isEqualTo(new TrashCleanupResult(1, 0));
        verify(cacheInvalidationService)
                .evictStatisticsAfterCommit(1001L);
        verify(cacheInvalidationService)
                .evictRecommendationsAfterCommit(1001L);
    }
}
