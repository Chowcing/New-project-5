package com.example.expense.transaction.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class TransactionResponse {
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
    private String note;
    private List<TransactionImageResponse> images = new ArrayList<>();

    public void setImages(List<TransactionImageResponse> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }
}
