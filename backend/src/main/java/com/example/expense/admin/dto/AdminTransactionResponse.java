package com.example.expense.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminTransactionResponse {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
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

}
