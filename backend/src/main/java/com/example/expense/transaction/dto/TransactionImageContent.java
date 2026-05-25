package com.example.expense.transaction.dto;

import org.springframework.core.io.Resource;

public record TransactionImageContent(
        Resource resource,
        String contentType,
        Long sizeBytes,
        String originalFilename
) {
}
