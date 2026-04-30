package com.example.expense.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        Long categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}

