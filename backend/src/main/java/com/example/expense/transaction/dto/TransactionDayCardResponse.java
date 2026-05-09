package com.example.expense.transaction.dto;

import com.example.expense.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDayCardResponse {
    private LocalDate date;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private BigDecimal balance;
    private long transactionCount;
    private PageResponse<TransactionResponse> records;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

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

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public long getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(long transactionCount) {
        this.transactionCount = transactionCount;
    }

    public PageResponse<TransactionResponse> getRecords() {
        return records;
    }

    public void setRecords(PageResponse<TransactionResponse> records) {
        this.records = records;
    }
}
