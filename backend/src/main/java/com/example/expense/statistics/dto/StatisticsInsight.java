package com.example.expense.statistics.dto;

import java.math.BigDecimal;

public record StatisticsInsight(
        String currentPeriod,
        String previousPeriod,
        BigDecimal previousTotalExpense,
        BigDecimal previousTotalIncome,
        BigDecimal previousBalance,
        BigDecimal expenseChangeAmount,
        BigDecimal expenseChangePercent,
        BigDecimal incomeChangeAmount,
        BigDecimal incomeChangePercent,
        BigDecimal balanceChangeAmount,
        BigDecimal balanceChangePercent,
        BigDecimal averageDailyExpense,
        BigDecimal averageExpensePerTransaction,
        PeakExpenseSummary peakExpense
) {
}
