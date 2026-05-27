package com.example.expense.transaction.scheduler;

import com.example.expense.transaction.service.TransactionImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionImageCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(TransactionImageCleanupScheduler.class);

    private final TransactionImageService transactionImageService;

    public TransactionImageCleanupScheduler(TransactionImageService transactionImageService) {
        this.transactionImageService = transactionImageService;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.time-zone:Asia/Shanghai}")
    public void cleanupDeletedPhysicalFiles() {
        int cleaned = transactionImageService.cleanupDeletedPhysicalFiles();
        if (cleaned > 0) {
            log.info("交易图片物理文件延迟清理完成 cleaned={}", cleaned);
        }
    }
}
