package com.example.expense.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.HexFormat;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String statisticsMonthly(Long userId, YearMonth month) {
        return userPrefix(userId) + ":monthly:" + month;
    }

    public static String statisticsWeekly(Long userId, LocalDate weekStart) {
        return userPrefix(userId) + ":weekly:" + weekStart;
    }

    public static String statisticsYearly(Long userId, Year year) {
        return userPrefix(userId) + ":yearly:" + year;
    }

    public static String categoryList(Long userId, String type) {
        return userPrefix(userId) + ":type:" + blank(type);
    }

    public static String paymentMethodList(Long userId) {
        return userPrefix(userId) + ":all";
    }

    public static String onlinePlatformList(Long userId) {
        return userPrefix(userId) + ":all";
    }

    public static String recommendTemplates(Long userId, String type, int limit) {
        return userPrefix(userId) + ":templates:" + hash(blank(type) + "|" + limit);
    }

    public static String recommendContextTemplates(
            Long userId,
            String itemName,
            String type,
            String channel,
            LocalDateTime occurredAt,
            int limit
    ) {
        return userPrefix(userId) + ":context:" + hash(blank(itemName) + "|" + blank(type) + "|"
                + blank(channel) + "|" + (occurredAt == null ? "" : occurredAt) + "|" + limit);
    }

    public static String recommendQuickEntry(Long userId, String type, int limit) {
        return userPrefix(userId) + ":quick-entry:" + hash(blank(type) + "|" + limit);
    }

    static String userPrefix(Long userId) {
        return "user:" + userId;
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
