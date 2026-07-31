package com.example.expense.transaction.scheduler;

import com.example.expense.transaction.dto.TrashCleanupResult;
import com.example.expense.transaction.service.TransactionTrashCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionTrashCleanupScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(TransactionTrashCleanupScheduler.class);

    private final TransactionTrashCleanupService cleanupService;

    public TransactionTrashCleanupScheduler(
            TransactionTrashCleanupService cleanupService
    ) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "${app.time-zone:Asia/Shanghai}")
    public void cleanupExpiredTransactions() {
        TrashCleanupResult result = cleanupService.cleanupExpired();
        log.info(
                "交易回收站自动清理完成 deletedCount={} failedCount={}",
                result.deletedCount(),
                result.failedCount());
    }
}
