package com.example.expense.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyStatisticsResponse(
        String month,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        BigDecimal balance,
        long transactionCount,
        long expenseCount,
        long incomeCount,
        List<DailySummary> dailyTrend,
        List<CategorySummary> expenseByCategory,
        List<CategorySummary> incomeByCategory,
        List<ChannelSummary> expenseByChannel,
        List<PaymentMethodSummary> expenseByPaymentMethod
) {
}
