package com.example.expense.auth.dto;

public record LoginRateLimitResponse(long retryAfterSeconds) {
}
