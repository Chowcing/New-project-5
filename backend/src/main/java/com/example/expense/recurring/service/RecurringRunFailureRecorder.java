package com.example.expense.recurring.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringRunFailureRecorder {
    private static final String RUN_FAILED = "FAILED";

    private final RecurringRuleRunMapper recurringRuleRunMapper;
    private final Clock clock;

    public RecurringRunFailureRecorder(RecurringRuleRunMapper recurringRuleRunMapper, Clock clock) {
        this.recurringRuleRunMapper = recurringRuleRunMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(RecurringRuleRun run, String message) {
        // 仅更新失败状态，不能把回滚事务中的流水 ID 或旧快照重新写回。
        recurringRuleRunMapper.update(null, new UpdateWrapper<RecurringRuleRun>()
                .eq("id", run.getId())
                .eq("user_id", run.getUserId())
                .in("status", "PENDING", RUN_FAILED)
                .isNull("transaction_id")
                .set("status", RUN_FAILED)
                .set("error_message", message)
                .set("processed_at", LocalDateTime.now(clock)));
    }
}
