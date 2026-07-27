package com.example.expense.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiSceneRecommendationRequest(
        @NotBlank @Size(max = 100) String itemName,
        @NotBlank @Pattern(regexp = "EXPENSE|INCOME") String type
) {
}
