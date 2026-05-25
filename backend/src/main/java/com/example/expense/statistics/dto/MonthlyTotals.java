package com.example.expense.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class MonthlyTotals {
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private Long transactionCount;
    private Long expenseCount;
    private Long incomeCount;

}
