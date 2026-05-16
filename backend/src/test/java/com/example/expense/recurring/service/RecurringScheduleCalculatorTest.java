package com.example.expense.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.recurring.entity.RecurringRule;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringScheduleCalculatorTest {

    @Test
    void monthlyRuleClampsToLastDayWhenMonthIsShorter() {
        RecurringRule rule = buildMonthlyRule(31, 1, LocalDate.of(2026, 1, 31), null);

        LocalDate nextRunDate = RecurringScheduleCalculator.calculateInitialNextRunDate(rule, LocalDate.of(2026, 2, 1));

        assertThat(nextRunDate).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void weeklyRuleAdvancesByIntervalWeeks() {
        RecurringRule rule = buildWeeklyRule("MONDAY", 2, LocalDate.of(2026, 5, 4), null);

        LocalDate nextRunDate = RecurringScheduleCalculator.calculateNextRunDateAfter(rule, LocalDate.of(2026, 5, 18));

        assertThat(nextRunDate).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void attentionDateUsesReminderDaysBefore() {
        assertThat(RecurringScheduleCalculator.calculateAttentionDate(LocalDate.of(2026, 5, 20), 3))
                .isEqualTo(LocalDate.of(2026, 5, 17));
    }

    private RecurringRule buildMonthlyRule(Integer dayOfMonth, Integer intervalValue, LocalDate startDate, LocalDate endDate) {
        RecurringRule rule = new RecurringRule();
        rule.setType("EXPENSE");
        rule.setItemName("房租");
        rule.setAmount(new BigDecimal("1000.00"));
        rule.setChannel("OFFLINE");
        rule.setPaymentMethodId(1L);
        rule.setCategoryId(1L);
        rule.setScheduleType("MONTHLY");
        rule.setIntervalValue(intervalValue);
        rule.setDayOfMonth(dayOfMonth);
        rule.setWeekday(null);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        return rule;
    }

    private RecurringRule buildWeeklyRule(String weekday, Integer intervalValue, LocalDate startDate, LocalDate endDate) {
        RecurringRule rule = new RecurringRule();
        rule.setType("EXPENSE");
        rule.setItemName("咖啡");
        rule.setAmount(new BigDecimal("18.00"));
        rule.setChannel("ONLINE");
        rule.setPaymentMethodId(1L);
        rule.setCategoryId(1L);
        rule.setScheduleType("WEEKLY");
        rule.setIntervalValue(intervalValue);
        rule.setDayOfMonth(null);
        rule.setWeekday(weekday);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        return rule;
    }
}

