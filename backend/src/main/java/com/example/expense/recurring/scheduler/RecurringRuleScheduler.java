package com.example.expense.recurring.scheduler;

import com.example.expense.recurring.service.RecurringRuleService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringRuleScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurringRuleScheduler.class);

    private final RecurringRuleService recurringRuleService;

    public RecurringRuleScheduler(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void seedDueRuns() {
        LocalDate today = LocalDate.now();
        recurringRuleService.seedDueRunsForAllUsers(today);
        log.debug("周期记账待办已同步 date={}", today);
    }
}
