package com.example.expense.admin.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {
    private List<String> usernames = List.of();

    public List<String> getUsernames() {
        return usernames;
    }

    public void setUsernames(List<String> usernames) {
        this.usernames = usernames;
    }

    public boolean isAdmin(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        Set<String> normalized = new HashSet<>();
        for (String adminUsername : usernames) {
            if (adminUsername != null && !adminUsername.isBlank()) {
                normalized.add(adminUsername.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized.contains(username.trim().toLowerCase(Locale.ROOT));
    }
}
