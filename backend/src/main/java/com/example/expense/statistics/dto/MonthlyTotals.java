package com.example.expense.statistics.dto;

import java.math.BigDecimal;

public class MonthlyTotals {
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private Long transactionCount;
    private Long expenseCount;
    private Long incomeCount;

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

    public Long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(Long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public Long getExpenseCount() {
        return expenseCount;
    }

    public void setExpenseCount(Long expenseCount) {
        this.expenseCount = expenseCount;
    }

    public Long getIncomeCount() {
        return incomeCount;
    }

    public void setIncomeCount(Long incomeCount) {
        this.incomeCount = incomeCount;
    }
}
