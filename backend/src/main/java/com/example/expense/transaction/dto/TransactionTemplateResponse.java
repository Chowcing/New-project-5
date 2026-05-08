package com.example.expense.transaction.dto;

import java.math.BigDecimal;

public record TransactionTemplateResponse(
        String type,
        String itemName,
        BigDecimal amount,
        String channel,
        String onlineApp,
        String offlinePlace,
        Long paymentMethodId,
        String paymentMethodName,
        Long categoryId,
        String categoryName,
        String note,
        String reason,
        double score
) {
}
