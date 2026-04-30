package com.example.expense.statistics.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyStatisticsResponse(
        String month,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        BigDecimal balance,
        List<CategorySummary> expenseByCategory,
        List<CategorySummary> incomeByCategory
) {
}

