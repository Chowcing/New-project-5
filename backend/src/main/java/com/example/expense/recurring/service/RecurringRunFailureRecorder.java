package com.example.expense.recurring.service;

import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringRunFailureRecorder {
    private static final String RUN_FAILED = "FAILED";

    private final RecurringRuleRunMapper recurringRuleRunMapper;

    public RecurringRunFailureRecorder(RecurringRuleRunMapper recurringRuleRunMapper) {
        this.recurringRuleRunMapper = recurringRuleRunMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(RecurringRuleRun run, String message) {
        run.setStatus(RUN_FAILED);
        run.setErrorMessage(message);
        run.setProcessedAt(LocalDateTime.now());
        recurringRuleRunMapper.updateById(run);
    }
}
