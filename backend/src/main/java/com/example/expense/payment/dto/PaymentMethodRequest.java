package com.example.expense.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentMethodRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 32) String icon,
        Integer sortOrder
) {
}

