package com.example.expense.businessaudit.dto;

import java.time.LocalDateTime;

public record BusinessAuditLogResponse(
        Long id,
        Long userId,
        String action,
        String targetType,
        Long targetId,
        String source,
        String status,
        String requestId,
        LocalDateTime createdAt
) {
}
