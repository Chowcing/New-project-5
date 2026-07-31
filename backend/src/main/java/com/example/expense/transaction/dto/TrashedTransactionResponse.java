package com.example.expense.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrashedTransactionResponse {
    private Long id;
    private String type;
    private String itemName;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
    private String channel;
    private String onlineApp;
    private Long onlinePlatformId;
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    private String categoryName;
    private String categoryIcon;
    private String note;
    private LocalDateTime trashedAt;
}
