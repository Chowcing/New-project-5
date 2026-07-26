package com.example.expense.transaction.service;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.QuickEntryRecommendationsResponse;
import com.example.expense.transaction.dto.TransactionRecommendationAggregateRow;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class TransactionRecommendationService {
    private static final Pattern INTENT_IGNORED_CHARS =
            Pattern.compile("[\\p{P}\\p{Z}\\s]+");

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
        return buildTemplateRecommendations(
                loadAggregates(userId, type, null, now),
                now,
                limit);
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
        String query = normalizeIntentText(itemName);
        if (query.isBlank()) {
            return List.of();
        }
        LocalDateTime now = occurredAt == null ? LocalDateTime.now(clock) : occurredAt;
        Map<String, IntentCandidate> candidates = new HashMap<>();
        for (TransactionRecommendationAggregateRow row
                : loadAggregates(userId, type, channel, now)) {
            candidates.computeIfAbsent(
                    intentKey(row),
                    ignored -> new IntentCandidate()
            ).addContext(row, query);
        }

        return candidates.values().stream()
                .filter(candidate -> candidate.contextConfident(now))
                .sorted(intentComparator(now, true))
                .limit(limit)
                .map(candidate -> candidate.toResponse(now, true))
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

    private List<TransactionRecommendationAggregateRow> loadAggregates(
            Long userId,
            String type,
            String channel,
            LocalDateTime occurredAt
    ) {
        int contextMinute = occurredAt.getHour() * 60 + occurredAt.getMinute();
        int contextDayOfWeek = occurredAt.getDayOfWeek().getValue() % 7 + 1;
        return transactionMapper.selectRecommendationAggregates(
                userId,
                blankToNull(type),
                blankToNull(channel),
                occurredAt,
                contextMinute,
                contextDayOfWeek);
    }

    private List<TransactionTemplateResponse> buildTemplateRecommendations(
            List<TransactionRecommendationAggregateRow> rows,
            LocalDateTime occurredAt,
            int limit
    ) {
        Map<String, IntentCandidate> candidates = new HashMap<>();
        for (TransactionRecommendationAggregateRow row : rows) {
            candidates.computeIfAbsent(
                    intentKey(row),
                    ignored -> new IntentCandidate()
            ).add(row);
        }
        return candidates.values().stream()
                .sorted(intentComparator(occurredAt, false))
                .limit(limit)
                .map(candidate -> candidate.toResponse(occurredAt, false))
                .toList();
    }

    private String normalizeIntentText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
        return INTENT_IGNORED_CHARS.matcher(normalized).replaceAll("");
    }

    private String intentKey(TransactionRecommendationAggregateRow row) {
        return row.getType() + "|" + row.getCategoryId() + "|"
                + normalizeIntentText(row.getItemName());
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

    private Comparator<IntentCandidate> intentComparator(
            LocalDateTime occurredAt,
            boolean includeTextScore
    ) {
        return Comparator
                .comparingDouble((IntentCandidate candidate) ->
                        candidate.score(occurredAt, includeTextScore))
                .reversed()
                .thenComparing(
                        candidate -> candidate.latest.getLatestOccurredAt(),
                        Comparator.reverseOrder())
                .thenComparing(
                        candidate -> candidate.latest.getLatestTransactionId(),
                        Comparator.reverseOrder());
    }

    private double textMatchScore(
            String query,
            TransactionRecommendationAggregateRow row
    ) {
        double score = 0;
        String itemName = normalizeIntentText(row.getItemName());
        String categoryName = normalizeIntentText(row.getCategoryName());
        String onlineApp = normalizeIntentText(row.getOnlineApp());
        String offlinePlace = normalizeIntentText(row.getOfflinePlace());
        if (!itemName.isBlank()) {
            if (itemName.equals(query)) {
                score += 130;
            } else if (itemName.startsWith(query) || query.startsWith(itemName)) {
                score += 95;
            } else if (itemName.contains(query) || query.contains(itemName)) {
                score += 80;
            }
        }
        if (!categoryName.isBlank() && categoryName.contains(query)) {
            score += 35;
        }
        if ((!onlineApp.isBlank() && onlineApp.contains(query))
                || (!offlinePlace.isBlank() && offlinePlace.contains(query))) {
            score += 30;
        }
        return Math.min(score, 130);
    }

    private final class IntentCandidate {
        private TransactionRecommendationAggregateRow latest;
        private long occurrenceCount;
        private long timeWindowHitCount;
        private long sameWeekdayCount;
        private long sameDayTypeCount;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private double bestTextScore;

        private void add(TransactionRecommendationAggregateRow row) {
            occurrenceCount += row.getOccurrenceCount();
            timeWindowHitCount += row.getTimeWindowHitCount();
            sameWeekdayCount += row.getSameWeekdayCount();
            sameDayTypeCount += row.getSameDayTypeCount();
            minAmount = minAmount == null || row.getMinAmount().compareTo(minAmount) < 0
                    ? row.getMinAmount() : minAmount;
            maxAmount = maxAmount == null || row.getMaxAmount().compareTo(maxAmount) > 0
                    ? row.getMaxAmount() : maxAmount;
            if (latest == null
                    || row.getLatestOccurredAt().isAfter(latest.getLatestOccurredAt())
                    || (row.getLatestOccurredAt().equals(latest.getLatestOccurredAt())
                        && row.getLatestTransactionId() > latest.getLatestTransactionId())) {
                latest = row;
            }
        }

        private void addContext(
                TransactionRecommendationAggregateRow row,
                String query
        ) {
            bestTextScore = Math.max(bestTextScore, textMatchScore(query, row));
            add(row);
        }

        private double baseScore(LocalDateTime occurredAt) {
            long days = Math.max(0, ChronoUnit.DAYS.between(
                    latest.getLatestOccurredAt().toLocalDate(),
                    occurredAt.toLocalDate()));
            double recencyScore = Math.max(0, 60 - days / 2.0);
            double sampleFactor = Math.min(1.0, occurrenceCount / 3.0);
            double timeRatio = timeWindowHitCount / (double) occurrenceCount;
            double weekdayRatio = sameWeekdayCount / (double) occurrenceCount;
            double dayTypeRatio = sameDayTypeCount / (double) occurrenceCount;
            double rawTimeScore = 40 * timeRatio * sampleFactor;
            double rawCalendarScore =
                    Math.max(16 * weekdayRatio, 6 * dayTypeRatio) * sampleFactor;
            double activityFactor = 0.2 + 0.8 * (recencyScore / 60.0);
            double patternScore =
                    (rawTimeScore + rawCalendarScore) * activityFactor;
            double frequencyScore = Math.min(occurrenceCount, 8) * 3;
            return 8 + recencyScore + patternScore + frequencyScore;
        }

        private double score(
                LocalDateTime occurredAt,
                boolean includeTextScore
        ) {
            return baseScore(occurredAt) + (includeTextScore ? bestTextScore : 0);
        }

        private boolean contextConfident(LocalDateTime occurredAt) {
            return bestTextScore >= 80
                    || (bestTextScore >= 35
                        && baseScore(occurredAt) + bestTextScore >= 95);
        }

        private TransactionTemplateResponse toResponse(
                LocalDateTime occurredAt,
                boolean includeTextScore
        ) {
            return new TransactionTemplateResponse(
                    latest.getType(),
                    latest.getItemName(),
                    latest.getAmount(),
                    latest.getChannel(),
                    latest.getOnlineApp(),
                    latest.getOnlinePlatformId(),
                    latest.getOfflinePlace(),
                    latest.getPaymentMethodId(),
                    latest.getPaymentMethodName(),
                    latest.getCategoryId(),
                    latest.getCategoryName(),
                    latest.getNote(),
                    reason(),
                    Math.round(score(occurredAt, includeTextScore) * 10.0) / 10.0
            );
        }

        private double timeRatio() {
            return timeWindowHitCount / (double) occurrenceCount;
        }

        private double weekdayRatio() {
            return sameWeekdayCount / (double) occurrenceCount;
        }

        private double dayTypeRatio() {
            return sameDayTypeCount / (double) occurrenceCount;
        }

        private String reason() {
            List<String> reasons = new ArrayList<>();
            if (occurrenceCount > 1) {
                reasons.add("历史出现 " + occurrenceCount + " 次");
            }
            if (occurrenceCount >= 2
                    && timeWindowHitCount >= 2
                    && timeRatio() >= 0.5) {
                reasons.add("常在当前时段记录");
            }
            if (occurrenceCount >= 3
                    && sameWeekdayCount >= 2
                    && weekdayRatio() >= 0.5) {
                reasons.add("同一星期习惯");
            } else if (occurrenceCount >= 3
                    && sameDayTypeCount >= 2
                    && dayTypeRatio() >= 2.0 / 3.0) {
                reasons.add("工作日/周末习惯相近");
            }
            if (minAmount.compareTo(maxAmount) != 0) {
                reasons.add("金额参考最近记录");
            }
            return reasons.isEmpty()
                    ? "最近使用的历史模板"
                    : String.join("，", reasons);
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
