package com.example.expense.user.dto;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String nickname,
        String status,
        boolean admin,
        LocalDateTime createdAt
) {
}
