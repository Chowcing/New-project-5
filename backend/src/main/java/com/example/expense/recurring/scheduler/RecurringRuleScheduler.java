package com.example.expense.recurring.scheduler;

import com.example.expense.recurring.service.RecurringRuleService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringRuleScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurringRuleScheduler.class);

    private final RecurringRuleService recurringRuleService;
    private final Clock clock;

    public RecurringRuleScheduler(RecurringRuleService recurringRuleService, Clock clock) {
        this.recurringRuleService = recurringRuleService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "${app.time-zone:Asia/Shanghai}")
    public void seedDueRuns() {
        LocalDate today = LocalDate.now(clock);
        recurringRuleService.seedDueRunsForAllUsers(today);
        log.debug("周期记账待办已同步 date={}", today);
    }
}
