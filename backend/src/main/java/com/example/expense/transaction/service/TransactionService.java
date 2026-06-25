package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final TransactionRecommendationService recommendationService;
    private final CacheInvalidationService cacheInvalidationService;
    private final BusinessAuditLogService businessAuditLogService;

    @Autowired
    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService,
            TransactionImageService transactionImageService,
            TransactionRecommendationService recommendationService,
            CacheInvalidationService cacheInvalidationService,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.onlinePlatformService = onlinePlatformService;
        this.transactionImageService = transactionImageService;
        this.recommendationService = recommendationService;
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

    public List<TransactionTemplateResponse> recommendTemplates(Long userId, String type, int limit) {
        return recommendationService.recommendTemplates(userId, type, limit);
    }

    public List<TransactionTemplateResponse> recommendContextTemplates(
            Long userId,
            String itemName,
            String type,
            String channel,
            LocalDateTime occurredAt,
            int limit
    ) {
        return recommendationService.recommendContextTemplates(userId, itemName, type, channel, occurredAt, limit);
    }

    public QuickEntryRecommendationsResponse recommendQuickEntry(Long userId, String type, int limit) {
        return recommendationService.recommendQuickEntry(userId, type, limit);
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
        businessAuditLogService.recordSuccess(userId, action, targetType, targetId, source);
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

    private void evictAfterTransactionChange(Long userId) {
        cacheInvalidationService.evictStatisticsAfterCommit(userId);
        cacheInvalidationService.evictRecommendationsAfterCommit(userId);
    }
}
