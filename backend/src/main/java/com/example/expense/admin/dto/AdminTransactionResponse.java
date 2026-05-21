package com.example.expense.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
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

    public String getPaymentMethodName() {
        return paymentMethodName;
    }

    public void setPaymentMethodName(String paymentMethodName) {
        this.paymentMethodName = paymentMethodName;
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
}
