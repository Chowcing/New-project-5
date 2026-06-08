package com.example.expense.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginCodeRequest(
        @NotBlank String challengeId
) {
}
