package com.example.expense.transaction.service;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.QuickEntryRecommendationsResponse;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TransactionRecommendationService {
    private final TransactionMapper transactionMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final OnlinePlatformService onlinePlatformService;
    private final Clock clock;

    public TransactionRecommendationService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            Clock clock
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.onlinePlatformService = onlinePlatformService;
        this.clock = clock;
    }

    @Cacheable(cacheNames = CacheNames.RECOMMENDATIONS, key = "T(com.example.expense.common.cache.CacheKeys).recommendTemplates(#userId, #type, #limit)")
    public List<TransactionTemplateResponse> recommendTemplates(Long userId, String type, int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        String normalizedType = blankToNull(type);
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, normalizedType, now.minusDays(180), now, null, null, null, null, 300, 0L);
        if (rows.isEmpty()) {
            rows = transactionMapper.selectRecords(userId, normalizedType, null, now, null, null, null, null, 300, 0L);
        }

        Map<String, TemplateCandidate> candidates = new HashMap<>();
        for (TransactionResponse row : rows) {
            if (!hasActiveReferences(userId, row)) {
                continue;
            }
            String key = templateKey(row);
            TemplateCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TemplateCandidate(row));
            candidate.add(row, now);
        }

        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(TemplateCandidate::score).reversed())
                .limit(limit)
                .map(TemplateCandidate::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.RECOMMENDATIONS, key = "T(com.example.expense.common.cache.CacheKeys).recommendContextTemplates(#userId, #itemName, #type, #channel, #occurredAt, #limit)")
    public List<TransactionTemplateResponse> recommendContextTemplates(
            Long userId,
            String itemName,
            String type,
            String channel,
            LocalDateTime occurredAt,
            int limit
    ) {
        String query = normalize(itemName);
        if (query.isBlank()) {
            return List.of();
        }
        LocalDateTime now = occurredAt == null ? LocalDateTime.now(clock) : occurredAt;
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, blankToNull(type), now.minusDays(180), now, blankToNull(channel), null, null, null, 300, 0L);
        if (rows.isEmpty()) {
            rows = transactionMapper.selectRecords(userId, blankToNull(type), null, now, blankToNull(channel), null, null, null, 300, 0L);
        }

        Map<String, TemplateCandidate> candidates = new HashMap<>();
        for (TransactionResponse row : rows) {
            if (!hasActiveReferences(userId, row)) {
                continue;
            }
            double textScore = textMatchScore(query, row);
            if (textScore < 25) {
                continue;
            }
            String key = templateKey(row);
            TemplateCandidate candidate = candidates.computeIfAbsent(key, ignored -> new TemplateCandidate(row));
            candidate.addContext(row, now, textScore);
        }

        return candidates.values().stream()
                .filter(TemplateCandidate::contextConfident)
                .sorted(Comparator.comparingDouble(TemplateCandidate::score).reversed())
                .limit(limit)
                .map(TemplateCandidate::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.RECOMMENDATIONS, key = "T(com.example.expense.common.cache.CacheKeys).recommendQuickEntry(#userId, #type, #limit)")
    public QuickEntryRecommendationsResponse recommendQuickEntry(Long userId, String type, int limit) {
        String normalizedType = blankToNull(type);
        LocalDateTime now = LocalDateTime.now(clock);
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, normalizedType, now.minusDays(180), now, null, null, null, null, 500, 0L);
        if (rows.isEmpty()) {
            rows = transactionMapper.selectRecords(userId, normalizedType, null, now, null, null, null, null, 500, 0L);
        }

        Map<Long, UsageStats> categoryStats = new HashMap<>();
        Map<Long, UsageStats> paymentStats = new HashMap<>();
        Map<Long, UsageStats> platformStats = new HashMap<>();
        Map<String, UsageStats> placeStats = new HashMap<>();
        for (TransactionResponse row : rows) {
            collect(categoryStats, row.getCategoryId(), row.getOccurredAt());
            collect(paymentStats, row.getPaymentMethodId(), row.getOccurredAt());
            collect(platformStats, row.getOnlinePlatformId(), row.getOccurredAt());
            if ("OFFLINE".equals(row.getChannel())) {
                String place = trimToNull(row.getOfflinePlace());
                if (place != null) {
                    placeStats.computeIfAbsent(place, ignored -> new UsageStats()).add(row.getOccurredAt());
                }
            }
        }

        int nextLimit = Math.max(1, Math.min(limit, 20));
        List<Category> categories = categoryService.list(userId, normalizedType).stream()
                .sorted((left, right) -> compareRecommended(
                        Boolean.TRUE.equals(left.getPinned()),
                        categoryStats.get(left.getId()),
                        timeSceneBoost(left, now),
                        left.getSortOrder(),
                        left.getId(),
                        Boolean.TRUE.equals(right.getPinned()),
                        categoryStats.get(right.getId()),
                        timeSceneBoost(right, now),
                        right.getSortOrder(),
                        right.getId()))
                .limit(nextLimit)
                .toList();
        List<PaymentMethod> paymentMethods = paymentMethodService.list(userId).stream()
                .sorted((left, right) -> compareRecommended(
                        Boolean.TRUE.equals(left.getPinned()),
                        paymentStats.get(left.getId()),
                        0,
                        left.getSortOrder(),
                        left.getId(),
                        Boolean.TRUE.equals(right.getPinned()),
                        paymentStats.get(right.getId()),
                        0,
                        right.getSortOrder(),
                        right.getId()))
                .limit(nextLimit)
                .toList();
        List<OnlinePlatform> onlinePlatforms = onlinePlatformService.list(userId).stream()
                .sorted((left, right) -> compareRecommended(
                        Boolean.TRUE.equals(left.getPinned()),
                        platformStats.get(left.getId()),
                        0,
                        left.getSortOrder(),
                        left.getId(),
                        Boolean.TRUE.equals(right.getPinned()),
                        platformStats.get(right.getId()),
                        0,
                        right.getSortOrder(),
                        right.getId()))
                .limit(nextLimit)
                .toList();
        List<String> offlinePlaces = placeStats.entrySet().stream()
                .sorted((left, right) -> left.getValue().compareTo(right.getValue()))
                .limit(nextLimit)
                .map(Map.Entry::getKey)
                .toList();
        List<TransactionTemplateResponse> combinations = recommendTemplates(userId, normalizedType, Math.min(nextLimit, 6));
        return new QuickEntryRecommendationsResponse(categories, paymentMethods, onlinePlatforms, offlinePlaces, combinations);
    }

    private boolean hasActiveReferences(Long userId, TransactionResponse row) {
        try {
            categoryService.requireOwned(userId, row.getCategoryId());
            paymentMethodService.requireOwned(userId, row.getPaymentMethodId());
            if ("ONLINE".equals(row.getChannel()) && row.getOnlinePlatformId() != null) {
                onlinePlatformService.requireOwned(userId, row.getOnlinePlatformId());
            }
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String templateKey(TransactionResponse row) {
        return String.join("|",
                normalize(row.getType()),
                normalize(row.getItemName()),
                normalize(row.getChannel()),
                normalize(row.getOnlineApp()),
                String.valueOf(row.getOnlinePlatformId()),
                normalize(row.getOfflinePlace()),
                String.valueOf(row.getPaymentMethodId()),
                String.valueOf(row.getCategoryId())
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void collect(Map<Long, UsageStats> stats, Long id, LocalDateTime occurredAt) {
        if (id != null) {
            stats.computeIfAbsent(id, ignored -> new UsageStats()).add(occurredAt);
        }
    }

    private int compareRecommended(
            boolean leftPinned,
            UsageStats leftStats,
            int leftBoost,
            Integer leftSortOrder,
            Long leftId,
            boolean rightPinned,
            UsageStats rightStats,
            int rightBoost,
            Integer rightSortOrder,
            Long rightId
    ) {
        int pinnedCompare = Boolean.compare(rightPinned, leftPinned);
        if (pinnedCompare != 0) {
            return pinnedCompare;
        }
        int boostCompare = Integer.compare(rightBoost, leftBoost);
        if (boostCompare != 0) {
            return boostCompare;
        }
        int statsCompare = compareUsage(leftStats, rightStats);
        if (statsCompare != 0) {
            return statsCompare;
        }
        int sortCompare = Integer.compare(leftSortOrder == null ? 0 : leftSortOrder, rightSortOrder == null ? 0 : rightSortOrder);
        if (sortCompare != 0) {
            return sortCompare;
        }
        return Long.compare(rightId == null ? 0L : rightId, leftId == null ? 0L : leftId);
    }

    private int compareUsage(UsageStats left, UsageStats right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private int timeSceneBoost(Category category, LocalDateTime now) {
        if (category == null || category.getName() == null || now == null) {
            return 0;
        }
        String name = category.getName();
        int hour = now.getHour();
        if ((hour >= 6 && hour <= 13 || hour >= 17 && hour <= 20)
                && ("餐饮".equals(name) || "外卖".equals(name))) {
            return 2;
        }
        if (hour >= 7 && hour <= 10 && "交通".equals(name)) {
            return 1;
        }
        return 0;
    }

    private double textMatchScore(String query, TransactionResponse row) {
        double score = 0;
        String itemName = normalize(row.getItemName());
        String categoryName = normalize(row.getCategoryName());
        String onlineApp = normalize(row.getOnlineApp());
        String offlinePlace = normalize(row.getOfflinePlace());
        String note = normalize(row.getNote());
        if (itemName.equals(query)) {
            score += 130;
        } else if (itemName.startsWith(query) || query.startsWith(itemName)) {
            score += 95;
        } else if (itemName.contains(query) || query.contains(itemName)) {
            score += 80;
        }
        if (categoryName.contains(query)) {
            score += 35;
        }
        if (onlineApp.contains(query) || offlinePlace.contains(query)) {
            score += 30;
        }
        if (note.contains(query)) {
            score += 18;
        }
        return score;
    }

    private static final class TemplateCandidate {
        private TransactionResponse template;
        private int count;
        private double contextScore;
        private double bestTextScore;
        private int minTimeDeltaMinutes = Integer.MAX_VALUE;
        private double bestTimeScore;
        private double bestRecencyScore;
        private boolean sameWeekday;
        private boolean sameDayType;
        private boolean amountChanged;

        private TemplateCandidate(TransactionResponse template) {
            this.template = template;
        }

        private void add(TransactionResponse row, LocalDateTime now) {
            if (template.getAmount().compareTo(row.getAmount()) != 0) {
                amountChanged = true;
            }
            if (row.getOccurredAt().isAfter(template.getOccurredAt())) {
                template = row;
            }
            count++;
            int timeDelta = timeDeltaMinutes(now, row.getOccurredAt());
            minTimeDeltaMinutes = Math.min(minTimeDeltaMinutes, timeDelta);
            sameWeekday = sameWeekday || now.getDayOfWeek() == row.getOccurredAt().getDayOfWeek();
            sameDayType = sameDayType || isWeekend(now.getDayOfWeek()) == isWeekend(row.getOccurredAt().getDayOfWeek());
            long days = Math.max(0, ChronoUnit.DAYS.between(row.getOccurredAt().toLocalDate(), now.toLocalDate()));
            bestTimeScore = Math.max(bestTimeScore, Math.max(0, 40 - timeDelta / 6.0));
            bestRecencyScore = Math.max(bestRecencyScore, Math.max(0, 60 - days / 2.0));
        }

        private void addContext(TransactionResponse row, LocalDateTime now, double textScore) {
            bestTextScore = Math.max(bestTextScore, textScore);
            add(row, now);
            contextScore += textScore;
        }

        private double score() {
            double weekdayScore = sameWeekday ? 16 : 0;
            double dayTypeScore = sameWeekday ? 0 : sameDayType ? 6 : 0;
            double frequencyScore = Math.min(count, 8) * 3;
            return bestTimeScore + bestRecencyScore + weekdayScore + dayTypeScore + frequencyScore + contextScore + 8;
        }

        private boolean contextConfident() {
            return bestTextScore >= 80 || (bestTextScore >= 35 && score() >= 95);
        }

        private TransactionTemplateResponse toResponse() {
            return new TransactionTemplateResponse(
                    template.getType(),
                    template.getItemName(),
                    template.getAmount(),
                    template.getChannel(),
                    template.getOnlineApp(),
                    template.getOnlinePlatformId(),
                    template.getOfflinePlace(),
                    template.getPaymentMethodId(),
                    template.getPaymentMethodName(),
                    template.getCategoryId(),
                    template.getCategoryName(),
                    template.getNote(),
                    reason(),
                    Math.round(score() * 10.0) / 10.0
            );
        }

        private String reason() {
            List<String> reasons = new ArrayList<>();
            if (count > 1) {
                reasons.add("历史出现 " + count + " 次");
            }
            if (minTimeDeltaMinutes <= 90) {
                reasons.add("常在当前时段记录");
            }
            if (sameWeekday) {
                reasons.add("同一星期习惯");
            } else if (sameDayType) {
                reasons.add("工作日/周末习惯相近");
            }
            if (amountChanged) {
                reasons.add("金额参考最近记录");
            }
            if (reasons.isEmpty()) {
                return "历史记录模板";
            }
            return String.join("，", reasons);
        }

        private static int timeDeltaMinutes(LocalDateTime now, LocalDateTime occurredAt) {
            int nowMinutes = now.getHour() * 60 + now.getMinute();
            int rowMinutes = occurredAt.getHour() * 60 + occurredAt.getMinute();
            int delta = Math.abs(nowMinutes - rowMinutes);
            return Math.min(delta, 1440 - delta);
        }

        private static boolean isWeekend(DayOfWeek dayOfWeek) {
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }
    }

    private static final class UsageStats {
        private int count;
        private LocalDateTime lastUsedAt;

        private void add(LocalDateTime occurredAt) {
            count++;
            if (occurredAt != null && (lastUsedAt == null || occurredAt.isAfter(lastUsedAt))) {
                lastUsedAt = occurredAt;
            }
        }

        private int compareTo(UsageStats other) {
            if (lastUsedAt != null || other.lastUsedAt != null) {
                if (lastUsedAt == null) {
                    return 1;
                }
                if (other.lastUsedAt == null) {
                    return -1;
                }
                int lastCompare = other.lastUsedAt.compareTo(lastUsedAt);
                if (lastCompare != 0) {
                    return lastCompare;
                }
            }
            return Integer.compare(other.count, count);
        }
    }
}
