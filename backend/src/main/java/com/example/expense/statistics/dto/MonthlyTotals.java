package com.example.expense.statistics.dto;

import java.math.BigDecimal;

public class MonthlyTotals {
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }
}

