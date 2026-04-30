package com.example.expense.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank @Size(max = 32) String name,
        @NotBlank @Size(max = 32) String type,
        @DecimalMin("0.00") BigDecimal balance,
        Integer sortOrder
) {
}

