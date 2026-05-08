package com.example.expense.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionRequest(
        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
        @NotBlank @Size(max = 64) String itemName,
        @NotNull @DecimalMin(value = "0.01", message = "必须大于等于 0.01")
        @Digits(integer = 10, fraction = 2, message = "整数位最多 10 位且最多保留 2 位小数") BigDecimal amount,
        @NotNull LocalDateTime occurredAt,
        @NotNull @Pattern(regexp = "ONLINE|OFFLINE") String channel,
        @Size(max = 64) String onlineApp,
        @Size(max = 128) String offlinePlace,
        @NotNull Long paymentMethodId,
        @NotNull Long categoryId,
        @Size(max = 255) String note
) {
}
