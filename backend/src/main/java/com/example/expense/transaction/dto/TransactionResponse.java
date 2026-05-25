package com.example.expense.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionResponse {
    private Long id;
    private String type;
    private String itemName;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
    private String channel;
    private String onlineApp;
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    private String categoryName;
    private String note;
    private List<TransactionImageResponse> images = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getOnlineApp() {
        return onlineApp;
    }

    public void setOnlineApp(String onlineApp) {
        this.onlineApp = onlineApp;
    }

    public String getOfflinePlace() {
        return offlinePlace;
    }

    public void setOfflinePlace(String offlinePlace) {
        this.offlinePlace = offlinePlace;
    }

    public Long getPaymentMethodId() {
        return paymentMethodId;
    }

    public void setPaymentMethodId(Long paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public String getPaymentMethodName() {
        return paymentMethodName;
    }

    public void setPaymentMethodName(String paymentMethodName) {
        this.paymentMethodName = paymentMethodName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<TransactionImageResponse> getImages() {
        return images;
    }

    public void setImages(List<TransactionImageResponse> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }
}
