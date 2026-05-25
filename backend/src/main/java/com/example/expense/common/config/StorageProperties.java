package com.example.expense.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String transactionImageDir = "uploads/transaction-images";

    public String getTransactionImageDir() {
        return transactionImageDir;
    }

    public void setTransactionImageDir(String transactionImageDir) {
        this.transactionImageDir = transactionImageDir;
    }
}
