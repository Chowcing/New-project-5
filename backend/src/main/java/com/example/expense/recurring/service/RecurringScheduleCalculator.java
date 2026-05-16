package com.example.expense.recurring.service;

import com.example.expense.recurring.entity.RecurringRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

public final class RecurringScheduleCalculator {
    private RecurringScheduleCalculator() {
    }

    public static LocalDate calculateInitialNextRunDate(RecurringRule rule, LocalDate referenceDate) {
        if ("WEEKLY".equals(rule.getScheduleType())) {
            return calculateWeeklyFirstRun(rule, referenceDate);
        }
        return calculateMonthlyFirstRun(rule, referenceDate);
    }

    public static LocalDate calculateNextRunDateAfter(RecurringRule rule, LocalDate dueDate) {
        if (dueDate == null) {
            return null;
        }
        if ("WEEKLY".equals(rule.getScheduleType())) {
            LocalDate next = dueDate.plusWeeks(normalizeInterval(rule.getIntervalValue()));
            return isAfterEndDate(rule, next) ? null : next;
        }
        LocalDate next = clampDayOfMonth(dueDate.plusMonths(normalizeInterval(rule.getIntervalValue())), rule.getDayOfMonth());
        return isAfterEndDate(rule, next) ? null : next;
    }

    public static LocalDate calculateAttentionDate(RecurringRule rule) {
        return calculateAttentionDate(rule.getNextRunDate(), rule.getReminderDaysBefore());
    }

    public static LocalDate calculateAttentionDate(LocalDate dueDate, Integer reminderDaysBefore) {
        if (dueDate == null) {
            return null;
        }
        int days = reminderDaysBefore == null ? 0 : Math.max(reminderDaysBefore, 0);
        return dueDate.minusDays(days);
    }

    public static String normalizeWeekday(String weekday) {
        if (weekday == null || weekday.isBlank()) {
            return null;
        }
        return weekday.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate calculateWeeklyFirstRun(RecurringRule rule, LocalDate referenceDate) {
        DayOfWeek weekday = DayOfWeek.valueOf(normalizeWeekday(rule.getWeekday()));
        LocalDate candidate = firstWeeklyOccurrence(rule.getStartDate(), weekday);
        while (candidate.isBefore(referenceDate)) {
            candidate = candidate.plusWeeks(normalizeInterval(rule.getIntervalValue()));
        }
        return isAfterEndDate(rule, candidate) ? null : candidate;
    }

    private static LocalDate calculateMonthlyFirstRun(RecurringRule rule, LocalDate referenceDate) {
        LocalDate candidate = firstMonthlyOccurrence(rule.getStartDate(), rule.getDayOfMonth());
        int interval = normalizeInterval(rule.getIntervalValue());
        while (candidate.isBefore(referenceDate)) {
            candidate = clampDayOfMonth(candidate.plusMonths(interval), rule.getDayOfMonth());
        }
        return isAfterEndDate(rule, candidate) ? null : candidate;
    }

    private static LocalDate firstWeeklyOccurrence(LocalDate startDate, DayOfWeek weekday) {
        LocalDate candidate = startDate;
        while (candidate.getDayOfWeek() != weekday) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static LocalDate firstMonthlyOccurrence(LocalDate startDate, Integer dayOfMonth) {
        LocalDate candidate = clampDayOfMonth(startDate, dayOfMonth);
        if (candidate.isBefore(startDate)) {
            candidate = clampDayOfMonth(startDate.plusMonths(1), dayOfMonth);
        }
        return candidate;
    }

    private static LocalDate clampDayOfMonth(LocalDate date, Integer dayOfMonth) {
        int targetDay = dayOfMonth == null ? date.getDayOfMonth() : Math.max(1, dayOfMonth);
        int day = Math.min(targetDay, date.lengthOfMonth());
        return date.withDayOfMonth(day);
    }

    private static boolean isAfterEndDate(RecurringRule rule, LocalDate candidate) {
        return rule.getEndDate() != null && candidate.isAfter(rule.getEndDate());
    }

    private static int normalizeInterval(Integer intervalValue) {
        return intervalValue == null || intervalValue < 1 ? 1 : intervalValue;
    }
}

