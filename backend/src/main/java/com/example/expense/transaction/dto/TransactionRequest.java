package com.example.expense.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
        @NotBlank @Size(max = 64) String itemName,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        @NotNull @Pattern(regexp = "ONLINE|OFFLINE") String channel,
        @Size(max = 64) String onlineApp,
        @Size(max = 128) String offlinePlace,
        @NotNull Long paymentMethodId,
        @NotNull Long categoryId,
        @Size(max = 255) String note
) {
}
