package com.example.expense.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminOverviewResponse(
        long totalUsers,
        long disabledUsers,
        long activeUsers30d,
        long totalTransactions,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        List<AdminDailyMetricResponse> dailyMetrics
) {
}
