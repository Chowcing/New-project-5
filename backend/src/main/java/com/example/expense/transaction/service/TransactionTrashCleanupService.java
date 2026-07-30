package com.example.expense.transaction.service;

import com.example.expense.transaction.dto.ExpiredTrashCandidate;
import com.example.expense.transaction.dto.TrashCleanupResult;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionTrashCleanupService {
    private static final int BATCH_SIZE = 200;

    private final TransactionMapper transactionMapper;
    private final TransactionService transactionService;
    private final Clock clock;

    public TransactionTrashCleanupService(
            TransactionMapper transactionMapper,
            TransactionService transactionService,
            Clock clock
    ) {
        this.transactionMapper = transactionMapper;
        this.transactionService = transactionService;
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
            for (ExpiredTrashCandidate candidate : batch) {
                afterId = candidate.id();
                try {
                    if (transactionService.autoDeleteExpired(
                            candidate.userId(), candidate.id(), runAt)) {
                        deleted++;
                    }
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
        }
    }
}
