package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.common.web.PageResponse;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.QuickEntryRecommendationsResponse;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionImageContent;
import com.example.expense.transaction.dto.TransactionImageResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionMapper transactionMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final OnlinePlatformService onlinePlatformService;
    private final TransactionImageService transactionImageService;
    private final Clock clock;
    private final CacheInvalidationService cacheInvalidationService;
    private final BusinessAuditLogService businessAuditLogService;

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            TransactionImageService transactionImageService,
            Clock clock,
            BusinessAuditLogService businessAuditLogService
    ) {
        this(transactionMapper, categoryService, paymentMethodService, onlinePlatformService, transactionImageService, clock, null, businessAuditLogService);
    }

    @Autowired
    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            TransactionImageService transactionImageService,
            Clock clock
    ) {
        this(transactionMapper, categoryService, paymentMethodService, onlinePlatformService, transactionImageService, clock, null, null);
    }

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            TransactionImageService transactionImageService,
            Clock clock,
            CacheInvalidationService cacheInvalidationService
    ) {
        this(transactionMapper, categoryService, paymentMethodService, onlinePlatformService, transactionImageService, clock, cacheInvalidationService, null);
    }

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            TransactionImageService transactionImageService,
            Clock clock,
            CacheInvalidationService cacheInvalidationService,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.onlinePlatformService = onlinePlatformService;
        this.transactionImageService = transactionImageService;
        this.clock = clock;
        this.cacheInvalidationService = cacheInvalidationService;
        this.businessAuditLogService = businessAuditLogService;
    }

    public PageResponse<TransactionResponse> list(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword,
            int page,
            int size
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long total = transactionMapper.countRecords(userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long offset = (long) (page - 1) * size;
        // 所有列表和导出查询统一从 Mapper 注入 userId 条件，避免前端传参造成跨用户读取。
        List<TransactionResponse> rows = transactionMapper.selectRecords(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword, size, offset);
        attachImages(userId, rows);
        return PageResponse.of(rows, total, page, size);
    }

    public TransactionDayCardsResponse dailyCards(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword,
            int dayPage,
            int daySize,
            int recordPage,
            int recordSize
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long totalRecords = transactionMapper.countRecords(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long totalDays = transactionMapper.countRecordDays(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        long dayOffset = (long) (dayPage - 1) * daySize;
        List<TransactionDayCardResponse> days = transactionMapper.selectDayCards(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword, daySize, dayOffset);
        long recordOffset = (long) (recordPage - 1) * recordSize;

        for (TransactionDayCardResponse day : days) {
            BigDecimal totalExpense = defaultMoney(day.getTotalExpense());
            BigDecimal totalIncome = defaultMoney(day.getTotalIncome());
            day.setTotalExpense(totalExpense);
            day.setTotalIncome(totalIncome);
            day.setBalance(totalIncome.subtract(totalExpense));

            LocalDateTime dayStart = day.getDate().atStartOfDay();
            LocalDateTime dayEnd = day.getDate().plusDays(1).atStartOfDay();
            List<TransactionResponse> rows = transactionMapper.selectRecords(
                    userId, type, dayStart, dayEnd, channel, categoryId, paymentMethodId, keyword, recordSize, recordOffset);
            attachImages(userId, rows);
            day.setRecords(PageResponse.of(rows, day.getTransactionCount(), recordPage, recordSize));
        }

        return TransactionDayCardsResponse.of(days, totalDays, totalRecords, dayPage, daySize);
    }

    public List<TransactionDayOptionResponse> dailyOptions(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            Long categoryId,
            Long paymentMethodId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<TransactionDayOptionResponse> days = transactionMapper.selectDayOptions(
                userId, type, startAt, endAt, channel, categoryId, paymentMethodId, keyword);
        for (TransactionDayOptionResponse day : days) {
            BigDecimal totalExpense = defaultMoney(day.getTotalExpense());
            BigDecimal totalIncome = defaultMoney(day.getTotalIncome());
            day.setTotalExpense(totalExpense);
            day.setTotalIncome(totalIncome);
            day.setBalance(totalIncome.subtract(totalExpense));
        }
        return days;
    }

    public List<TransactionResponse> listAll(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        List<TransactionResponse> rows = transactionMapper.selectRecords(userId, type, startAt, endAt, null, categoryId, null, keyword, null, null);
        attachImages(userId, rows);
        return rows;
    }

    public TransactionResponse get(Long userId, Long id) {
        TransactionResponse response = transactionMapper.selectRecord(userId, id);
        if (response == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        attachImages(userId, List.of(response));
        return response;
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
        List<TransactionTemplateResponse> combinations = recommendTemplates(userId, normalizedType, Math.min(nextLimit, 10));
        return new QuickEntryRecommendationsResponse(categories, paymentMethods, onlinePlatforms, offlinePlaces, combinations);
    }

    public ExpenseTransaction create(Long userId, TransactionRequest request) {
        ensureOwnedReferences(userId, request);
        validateContext(request);
        ExpenseTransaction transaction = toEntity(new ExpenseTransaction(), userId, request);
        transactionMapper.insert(transaction);
        log.info("新增交易记录 userId={} transactionId={}", userId, transaction.getId());
        evictAfterTransactionChange(userId);
        audit(userId, "TRANSACTION_CREATE", "TRANSACTION", transaction.getId(), "USER");
        return transaction;
    }

    @Transactional
    public TransactionResponse createResponse(Long userId, TransactionRequest request, List<MultipartFile> images) {
        transactionImageService.validateFiles(images);
        ExpenseTransaction transaction = create(userId, request);
        transactionImageService.storeImages(userId, transaction, images);
        return get(userId, transaction.getId());
    }

    public boolean existsSameTransaction(Long userId, TransactionRequest request) {
        LambdaQueryWrapper<ExpenseTransaction> wrapper = new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getUserId, userId)
                .eq(ExpenseTransaction::getType, request.type())
                .eq(ExpenseTransaction::getAmount, request.amount())
                .eq(ExpenseTransaction::getOccurredAt, request.occurredAt())
                .eq(ExpenseTransaction::getChannel, request.channel())
                .eq(ExpenseTransaction::getPaymentMethodId, request.paymentMethodId())
                .eq(ExpenseTransaction::getCategoryId, request.categoryId());
        applyNullableEq(wrapper, ExpenseTransaction::getItemName, trimToNull(request.itemName()));
        applyNullableEq(wrapper, ExpenseTransaction::getOnlineApp,
                "ONLINE".equals(request.channel()) ? trimToNull(request.onlineApp()) : null);
        if ("ONLINE".equals(request.channel()) && request.onlinePlatformId() != null) {
            wrapper.eq(ExpenseTransaction::getOnlinePlatformId, request.onlinePlatformId());
        }
        applyNullableEq(wrapper, ExpenseTransaction::getOfflinePlace,
                "OFFLINE".equals(request.channel()) ? trimToNull(request.offlinePlace()) : null);
        applyNullableEq(wrapper, ExpenseTransaction::getNote, trimToNull(request.note()));
        return transactionMapper.selectCount(wrapper) > 0;
    }

    public ExpenseTransaction update(Long userId, Long id, TransactionRequest request) {
        ExpenseTransaction transaction = requireOwned(userId, id);
        ensureOwnedReferences(userId, request);
        validateContext(request);
        toEntity(transaction, userId, request);
        transactionMapper.updateById(transaction);
        log.info("更新交易记录 userId={} transactionId={}", userId, id);
        evictAfterTransactionChange(userId);
        audit(userId, "TRANSACTION_UPDATE", "TRANSACTION", id, "USER");
        return transaction;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        deleteInternal(userId, id, true);
    }

    @Transactional
    public void deleteWithoutBusinessAudit(Long userId, Long id) {
        deleteInternal(userId, id, false);
    }

    private void deleteInternal(Long userId, Long id, boolean writeBusinessAudit) {
        requireOwned(userId, id);
        transactionImageService.softDeleteByTransaction(userId, id);
        transactionMapper.deleteById(id);
        log.info("删除交易记录 userId={} transactionId={}", userId, id);
        evictAfterTransactionChange(userId);
        if (writeBusinessAudit) {
            audit(userId, "TRANSACTION_DELETE", "TRANSACTION", id, "USER");
        }
    }

    public List<TransactionImageResponse> appendImages(Long userId, Long transactionId, List<MultipartFile> images) {
        return transactionImageService.appendImages(userId, transactionId, images);
    }

    public void deleteImage(Long userId, Long transactionId, Long imageId) {
        transactionImageService.deleteImage(userId, transactionId, imageId);
        audit(userId, "TRANSACTION_IMAGE_DELETE", "TRANSACTION_IMAGE", imageId, "USER");
    }

    public TransactionImageContent readImage(Long userId, Long transactionId, Long imageId) {
        return transactionImageService.readImage(userId, transactionId, imageId);
    }

    private void attachImages(Long userId, List<TransactionResponse> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(TransactionResponse::getId).toList();
        Map<Long, List<TransactionImageResponse>> imagesByTransactionId =
                transactionImageService.listImagesByTransactionIds(userId, ids);
        if (imagesByTransactionId == null) {
            imagesByTransactionId = Map.of();
        }
        for (TransactionResponse row : rows) {
            row.setImages(imagesByTransactionId.getOrDefault(row.getId(), List.of()));
        }
    }

    private void audit(Long userId, String action, String targetType, Long targetId, String source) {
        if (businessAuditLogService != null) {
            businessAuditLogService.recordSuccess(userId, action, targetType, targetId, source);
        }
    }

    private ExpenseTransaction requireOwned(Long userId, Long id) {
        ExpenseTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getId, id)
                .eq(ExpenseTransaction::getUserId, userId));
        if (transaction == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        return transaction;
    }

    private void ensureOwnedReferences(Long userId, TransactionRequest request) {
        categoryService.requireOwned(userId, request.categoryId());
        paymentMethodService.requireOwned(userId, request.paymentMethodId());
        if ("ONLINE".equals(request.channel()) && request.onlinePlatformId() != null) {
            onlinePlatformService.requireOwned(userId, request.onlinePlatformId());
        }
    }

    private void validateContext(TransactionRequest request) {
        // 线上/线下的上下文字段不同，这里统一收口校验，避免前端绕过表单后写入半结构化脏数据。
        if ("OFFLINE".equals(request.channel()) && isBlank(request.offlinePlace())) {
            throw new IllegalArgumentException("线下记录需要填写地点");
        }
        if ("ONLINE".equals(request.channel())
                && "EXPENSE".equals(request.type())
                && request.onlinePlatformId() == null
                && isBlank(request.onlineApp())) {
            throw new IllegalArgumentException("线上支出需要填写消费 APP");
        }
    }

    private ExpenseTransaction toEntity(ExpenseTransaction transaction, Long userId, TransactionRequest request) {
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setItemName(trimToNull(request.itemName()));
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setChannel(request.channel());
        OnlinePlatform onlinePlatform = resolveOnlinePlatform(userId, request);
        transaction.setOnlinePlatformId(onlinePlatform == null ? null : onlinePlatform.getId());
        transaction.setOnlineApp(resolveOnlineApp(request, onlinePlatform));
        transaction.setOfflinePlace("OFFLINE".equals(request.channel()) ? trimToNull(request.offlinePlace()) : null);
        PaymentMethod paymentMethod = paymentMethodService.requireOwned(userId, request.paymentMethodId());
        transaction.setPaymentMethodId(paymentMethod.getId());
        transaction.setPaymentMethodName(paymentMethod.getName());
        transaction.setCategoryId(request.categoryId());
        transaction.setNote(trimToNull(request.note()));
        return transaction;
    }

    private OnlinePlatform resolveOnlinePlatform(Long userId, TransactionRequest request) {
        if (!"ONLINE".equals(request.channel()) || request.onlinePlatformId() == null) {
            return null;
        }
        return onlinePlatformService.requireOwned(userId, request.onlinePlatformId());
    }

    private String resolveOnlineApp(TransactionRequest request, OnlinePlatform onlinePlatform) {
        if (!"ONLINE".equals(request.channel())) {
            return null;
        }
        String snapshotName = trimToNull(request.onlineApp());
        if (snapshotName != null) {
            return snapshotName;
        }
        if (onlinePlatform != null) {
            return onlinePlatform.getName();
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private <T> void applyNullableEq(
            LambdaQueryWrapper<ExpenseTransaction> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<ExpenseTransaction, T> column,
            T value
    ) {
        if (value == null) {
            wrapper.isNull(column);
        } else {
            wrapper.eq(column, value);
        }
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

    private void evictAfterTransactionChange(Long userId) {
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictStatisticsAfterCommit(userId);
            cacheInvalidationService.evictRecommendationsAfterCommit(userId);
        }
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
        private final TransactionResponse template;
        private int count;
        private double score;
        private double bestTextScore;
        private int minTimeDeltaMinutes = Integer.MAX_VALUE;
        private boolean sameWeekday;
        private boolean sameDayType;
        private boolean amountChanged;

        private TemplateCandidate(TransactionResponse template) {
            this.template = template;
        }

        private void add(TransactionResponse row, LocalDateTime now) {
            count++;
            int timeDelta = timeDeltaMinutes(now, row.getOccurredAt());
            minTimeDeltaMinutes = Math.min(minTimeDeltaMinutes, timeDelta);
            sameWeekday = sameWeekday || now.getDayOfWeek() == row.getOccurredAt().getDayOfWeek();
            sameDayType = sameDayType || isWeekend(now.getDayOfWeek()) == isWeekend(row.getOccurredAt().getDayOfWeek());
            long days = Math.max(0, ChronoUnit.DAYS.between(row.getOccurredAt().toLocalDate(), now.toLocalDate()));
            double timeScore = Math.max(0, 40 - timeDelta / 6.0);
            double weekdayScore = now.getDayOfWeek() == row.getOccurredAt().getDayOfWeek() ? 16 : 0;
            double dayTypeScore = isWeekend(now.getDayOfWeek()) == isWeekend(row.getOccurredAt().getDayOfWeek()) ? 6 : 0;
            double recencyScore = Math.max(0, 20 - days / 6.0);
            score += timeScore + weekdayScore + dayTypeScore + recencyScore + 8;
            amountChanged = amountChanged || template.getAmount().compareTo(row.getAmount()) != 0;
        }

        private void addContext(TransactionResponse row, LocalDateTime now, double textScore) {
            bestTextScore = Math.max(bestTextScore, textScore);
            add(row, now);
            score += textScore;
        }

        private double score() {
            return score + Math.min(count, 8) * 6;
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
