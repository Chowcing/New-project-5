package com.example.expense.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record BudgetRequest(
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String month,
        Long categoryId,
        @NotNull @DecimalMin(value = "0.01", message = "必须大于等于 0.01")
        @Digits(integer = 10, fraction = 2, message = "整数位最多 10 位且最多保留 2 位小数") BigDecimal amount
) {
}
