package com.example.expense.auth.dto;

public record AuthLoginStartResponse(
        String status,
        String challengeId,
        String email
) {
}
