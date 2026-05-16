package com.example.expense.recurring.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringRuleRequest(
        @NotBlank @Size(max = 64) String name,
        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
        @NotBlank @Size(max = 64) String itemName,
        @NotNull @DecimalMin(value = "0.01", message = "必须大于等于 0.01")
        @Digits(integer = 10, fraction = 2, message = "整数位最多 10 位且最多保留 2 位小数") BigDecimal amount,
        @NotNull @Pattern(regexp = "ONLINE|OFFLINE") String channel,
        @Size(max = 64) String onlineApp,
        @Size(max = 128) String offlinePlace,
        @NotNull Long paymentMethodId,
        @NotNull Long categoryId,
        @Size(max = 255) String note,
        @NotNull @Pattern(regexp = "MONTHLY|WEEKLY") String scheduleType,
        @NotNull @Min(1) @Max(12) Integer intervalValue,
        @Min(1) @Max(31) Integer dayOfMonth,
        @Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY") String weekday,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull @Min(0) @Max(30) Integer reminderDaysBefore,
        @NotNull @Pattern(regexp = "ACTIVE|PAUSED") String status
) {
}

