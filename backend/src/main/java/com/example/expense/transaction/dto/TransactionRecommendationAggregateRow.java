package com.example.expense.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TransactionRecommendationAggregateRow {
    private Long latestTransactionId;
    private String type;
    private String itemName;
    private BigDecimal amount;
    private LocalDateTime latestOccurredAt;
    private String channel;
    private String onlineApp;
    private Long onlinePlatformId;
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    private String categoryName;
    private String note;
    private long occurrenceCount;
    private long timeWindowHitCount;
    private long sameWeekdayCount;
    private long sameDayTypeCount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}
