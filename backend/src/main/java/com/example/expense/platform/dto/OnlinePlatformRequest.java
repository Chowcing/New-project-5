package com.example.expense.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OnlinePlatformRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 32) String icon,
        Integer sortOrder,
        Boolean pinned
) {
}
