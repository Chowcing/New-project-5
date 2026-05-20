package com.example.expense.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

public record YearlyStatisticsResponse(
        String year,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        BigDecimal balance,
        long transactionCount,
        long expenseCount,
        long incomeCount,
        StatisticsInsight insight,
        List<MonthlyTrendSummary> monthlyTrend,
        List<CategorySummary> expenseByCategory,
        List<CategorySummary> incomeByCategory,
        List<ChannelSummary> expenseByChannel,
        List<PaymentMethodSummary> expenseByPaymentMethod
) {
}
