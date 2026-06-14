package com.example.expense.admin.dto;

public record AdminAttentionItemResponse(
        String key,
        String title,
        long value,
        String severity,
        String description,
        String route
) {
}
