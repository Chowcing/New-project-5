package com.example.expense.statistics.dto;

import java.math.BigDecimal;

public record PeakExpenseSummary(
        String period,
        String label,
        BigDecimal amount,
        long transactionCount
) {
}
