package com.example.expense.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiItemNameSuggestionRequest(
        @NotBlank @Size(max = 64) String itemName
) {
}
