package com.example.expense.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailCodeProperties {
    private String from = "noreply@example.com";
    private boolean localLogEnabled = true;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isLocalLogEnabled() {
        return localLogEnabled;
    }

    public void setLocalLogEnabled(boolean localLogEnabled) {
        this.localLogEnabled = localLogEnabled;
    }
}
