package com.example.expense.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminUserStatisticsResponse {
    private long transactionCount;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private LocalDateTime lastTransactionAt;

}
