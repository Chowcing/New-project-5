package com.example.expense.admin.dto;

public record AdminUserDetailResponse(
        AdminUserResponse user,
        AdminUserStatisticsResponse statistics
) {
}
