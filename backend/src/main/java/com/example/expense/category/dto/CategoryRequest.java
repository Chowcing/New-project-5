package com.example.expense.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 32) String name,
        @NotNull @Pattern(regexp = "EXPENSE|INCOME") String type,
        @Size(max = 32) String icon,
        Integer sortOrder,
        Boolean pinned
) {
}
