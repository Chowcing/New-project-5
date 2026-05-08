package com.example.expense.imports.dto;

public record ImportRowError(
        int rowNumber,
        String message
) {
}
