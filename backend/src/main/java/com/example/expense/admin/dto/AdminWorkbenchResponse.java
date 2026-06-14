package com.example.expense.admin.dto;

import java.util.List;

public record AdminWorkbenchResponse(
        AdminOverviewResponse overview,
        List<AdminAttentionItemResponse> attentionItems,
        List<AdminDailyMetricResponse> dailyMetrics,
        List<AdminTransactionResponse> recentRiskTransactions,
        List<AdminAuditLogResponse> recentAuditLogs
) {
}
