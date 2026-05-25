package com.example.expense.transaction.dto;

public record TransactionImageResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        String url,
        Integer sortOrder
) {
}
