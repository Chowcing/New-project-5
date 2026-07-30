package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private TransactionTrashCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new TransactionTrashCleanupService(
                transactionMapper,
                transactionService,
                CLOCK);
    }

    @Test
    void cleanupExpiredUsesIdCursorAndContinuesAfterIndividualFailure() {
        ExpiredTrashCandidate first = new ExpiredTrashCandidate(11L, 1001L);
        ExpiredTrashCandidate failed = new ExpiredTrashCandidate(12L, 1002L);
        ExpiredTrashCandidate noLongerExpired =
                new ExpiredTrashCandidate(21L, 1003L);
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 0L, 200))
                .thenReturn(List.of(first, failed));
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 12L, 200))
                .thenReturn(List.of(noLongerExpired));
        when(transactionMapper.selectExpiredTrashCandidates(RUN_AT, 21L, 200))
                .thenReturn(List.of());
        when(transactionService.autoDeleteExpired(1001L, 11L, RUN_AT))
                .thenReturn(true);
        when(transactionService.autoDeleteExpired(1002L, 12L, RUN_AT))
                .thenThrow(new IllegalStateException("单条清理失败"));
        when(transactionService.autoDeleteExpired(1003L, 21L, RUN_AT))
                .thenReturn(false);

        TrashCleanupResult result = cleanupService.cleanupExpired();

        assertThat(result).isEqualTo(new TrashCleanupResult(1, 1));
        InOrder queryOrder = inOrder(transactionMapper);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 0L, 200);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 12L, 200);
        queryOrder.verify(transactionMapper)
                .selectExpiredTrashCandidates(RUN_AT, 21L, 200);
        verify(transactionService).autoDeleteExpired(1001L, 11L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1002L, 12L, RUN_AT);
        verify(transactionService).autoDeleteExpired(1003L, 21L, RUN_AT);
    }
}
