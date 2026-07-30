package com.example.expense.transaction.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.transaction.dto.TrashCleanupResult;
import com.example.expense.transaction.service.TransactionTrashCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class TransactionTrashCleanupSchedulerTest {
    @Mock
    private TransactionTrashCleanupService cleanupService;

    @Test
    void cleanupExpiredTransactionsDelegatesToCleanupService() {
        when(cleanupService.cleanupExpired())
                .thenReturn(new TrashCleanupResult(3, 1));
        TransactionTrashCleanupScheduler scheduler =
                new TransactionTrashCleanupScheduler(cleanupService);

        scheduler.cleanupExpiredTransactions();

        verify(cleanupService).cleanupExpired();
    }

    @Test
    void cleanupExpiredTransactionsRunsDailyAtThreeInConfiguredShanghaiZone()
            throws Exception {
        Scheduled scheduled = TransactionTrashCleanupScheduler.class
                .getMethod("cleanupExpiredTransactions")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron())
                .isEqualTo("0 0 3 * * *");
        assertThat(scheduled.zone())
                .isEqualTo("${app.time-zone:Asia/Shanghai}");
    }
}
