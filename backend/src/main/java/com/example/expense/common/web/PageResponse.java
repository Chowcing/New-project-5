package com.example.expense.common.web;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        int page,
        int size,
        long totalPages
) {
    public static <T> PageResponse<T> of(List<T> records, long total, int page, int size) {
        long totalPages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResponse<>(records, total, page, size, totalPages);
    }
}
