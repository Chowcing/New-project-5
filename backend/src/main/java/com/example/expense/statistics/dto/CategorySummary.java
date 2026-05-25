package com.example.expense.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CategorySummary {
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private Long transactionCount;

}
