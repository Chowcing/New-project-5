package com.example.expense.imports.dto;

import java.time.LocalDateTime;

public record ImportJobResponse(
        Long id,
        String originalFilename,
        String status,
        int totalRows,
        int importedRows,
        int failedRows,
        ImportResult result,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
