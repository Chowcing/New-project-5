package com.example.expense.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        @NotNull Long categoryId,
        @NotNull Long accountId,
        @Size(max = 255) String note
) {
}

