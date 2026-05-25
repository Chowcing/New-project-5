package com.example.expense.transaction.dto;

import lombok.Getter;
import lombok.Setter;

import com.example.expense.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class TransactionDayCardResponse {
    private LocalDate date;
    private BigDecimal totalExpense;
    private BigDecimal totalIncome;
    private BigDecimal balance;
    private long transactionCount;
    private PageResponse<TransactionResponse> records;

}
