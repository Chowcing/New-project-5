package com.example.expense.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}

