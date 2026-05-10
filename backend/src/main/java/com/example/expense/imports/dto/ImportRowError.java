package com.example.expense.imports.dto;

public record ImportRowError(
        int rowNumber,
        String errorType,
        String message,
        String type,
        String itemName,
        String amount,
        String occurredAt,
        String channel,
        String onlineApp,
        String offlinePlace,
        String paymentMethodName,
        String categoryName,
        String note
) {
}
