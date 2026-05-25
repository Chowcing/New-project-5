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
    private String offlinePlace;
    private String paymentMethodName;
    private String categoryName;
    private String note;

}
