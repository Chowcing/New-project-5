package com.example.expense.transaction.dto;

import java.util.List;

public record TransactionDayCardsResponse(
        List<TransactionDayCardResponse> days,
        long totalDays,
        long totalRecords,
        int dayPage,
        int daySize,
        long totalDayPages
) {
    public static TransactionDayCardsResponse of(
            List<TransactionDayCardResponse> days,
            long totalDays,
            long totalRecords,
            int dayPage,
            int daySize
    ) {
        long totalDayPages = daySize <= 0 ? 0 : (totalDays + daySize - 1) / daySize;
        return new TransactionDayCardsResponse(days, totalDays, totalRecords, dayPage, daySize, totalDayPages);
    }
}
