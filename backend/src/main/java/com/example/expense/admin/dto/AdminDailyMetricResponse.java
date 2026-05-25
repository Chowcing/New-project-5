package com.example.expense.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AdminDailyMetricResponse {
    private LocalDate date;
    private long transactionCount;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private long activeUsers;

}
