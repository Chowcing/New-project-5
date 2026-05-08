package com.example.expense.imports.dto;

import java.util.List;

public record ImportResult(
        int totalRows,
        int importedRows,
        int failedRows,
        List<ImportRowError> errors
) {
}
