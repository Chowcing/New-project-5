package com.example.expense.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.common.web.PageResponse;
import com.example.expense.common.init.DefaultDataSeeds;
import com.example.expense.payment.dto.PaymentMethodRequest;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.mapper.PaymentMethodMapper;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

@Service
public class PaymentMethodService {
    private final PaymentMethodMapper paymentMethodMapper;
    private final TransactionMapper transactionMapper;
    private final RecurringRuleMapper recurringRuleMapper;
    private final CacheInvalidationService cacheInvalidationService;
    private final BusinessAuditLogService businessAuditLogService;

    @Autowired
    public PaymentMethodService(
            PaymentMethodMapper paymentMethodMapper,
            TransactionMapper transactionMapper,
            RecurringRuleMapper recurringRuleMapper,
            CacheInvalidationService cacheInvalidationService,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.paymentMethodMapper = paymentMethodMapper;
        this.transactionMapper = transactionMapper;
        this.recurringRuleMapper = recurringRuleMapper;
        this.cacheInvalidationService = cacheInvalidationService;
        this.businessAuditLogService = businessAuditLogService;
    }

    @Cacheable(cacheNames = CacheNames.PAYMENT_METHODS, key = "T(com.example.expense.common.cache.CacheKeys).paymentMethodList(#userId)")
    public List<PaymentMethod> list(Long userId) {
        return selectOwnedList(userId);
    }

    public PaymentMethod create(Long userId, PaymentMethodRequest request) {
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, null);
        PaymentMethod method = toEntity(new PaymentMethod(), userId, request, name);
        paymentMethodMapper.insert(method);
        evictAfterCreate(userId);
        audit(userId, "PAYMENT_METHOD_CREATE", method.getId());
        return method;
    }

    public PaymentMethod update(Long userId, Long id, PaymentMethodRequest request) {
        PaymentMethod method = requireOwned(userId, id);
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, id);
        toEntity(method, userId, request, name);
        paymentMethodMapper.updateById(method);
        evictAfterUpdateOrDelete(userId);
        audit(userId, "PAYMENT_METHOD_UPDATE", id);
        return method;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        long referenceCount = transactionMapper.countRecords(userId, null, null, null, null, null, id, null);
        long recurringReferenceCount = recurringRuleMapper.selectCount(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getPaymentMethodId, id)
                .eq(RecurringRule::getDeleted, 0));
        long totalReferences = referenceCount + recurringReferenceCount;
        if (totalReferences > 0) {
            throw new IllegalArgumentException("支付方式已被 " + totalReferences + " 条记录或周期规则引用，不能删除");
        }
        paymentMethodMapper.deleteById(id);
        evictAfterUpdateOrDelete(userId);
        audit(userId, "PAYMENT_METHOD_DELETE", id);
    }

    public PageResponse<TransactionResponse> references(Long userId, Long id, int size) {
        requireOwned(userId, id);
        long total = transactionMapper.countRecords(userId, null, null, null, null, null, id, null);
        List<TransactionResponse> records = transactionMapper.selectRecords(
                userId, null, null, null, null, null, id, null, size, 0L);
        return PageResponse.of(records, total, 1, size);
    }

    public PaymentMethod requireOwned(Long userId, Long id) {
        PaymentMethod method = paymentMethodMapper.selectOne(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getId, id)
                .eq(PaymentMethod::getUserId, userId));
        if (method == null) {
            throw new IllegalArgumentException("支付方式不存在");
        }
        return method;
    }

    public void createDefaults(Long userId) {
        for (DefaultDataSeeds.PaymentMethodSeed seed : DefaultDataSeeds.PAYMENT_METHOD_SEEDS) {
            createDefaultIfMissing(userId, seed);
        }
        evictAfterCreate(userId);
    }

    private List<PaymentMethod> selectOwnedList(Long userId) {
        return paymentMethodMapper.selectList(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getUserId, userId)
                .orderByDesc(PaymentMethod::getPinned)
                .orderByAsc(PaymentMethod::getSortOrder)
                .orderByDesc(PaymentMethod::getId));
    }

    private void createDefaultIfMissing(Long userId, DefaultDataSeeds.PaymentMethodSeed seed) {
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getUserId, userId)
                .eq(PaymentMethod::getName, seed.name()));
        if (count != null && count > 0) {
            return;
        }

        PaymentMethod method = new PaymentMethod();
        method.setUserId(userId);
        method.setName(seed.name());
        method.setIcon(seed.icon());
        method.setSortOrder(seed.sortOrder());
        method.setPinned(false);
        try {
            paymentMethodMapper.insert(method);
        } catch (DuplicateKeyException ignored) {
            // 并发初始化时可能被其他线程先插入，忽略重复键即可保持幂等。
        }
    }

    private void ensureNameAvailable(Long userId, String name, Long excludedId) {
        Long count = paymentMethodMapper.selectCount(new LambdaQueryWrapper<PaymentMethod>()
                .eq(PaymentMethod::getUserId, userId)
                .eq(PaymentMethod::getName, name)
                .ne(excludedId != null, PaymentMethod::getId, excludedId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("支付方式已存在");
        }
    }

    private PaymentMethod toEntity(PaymentMethod method, Long userId, PaymentMethodRequest request, String name) {
        method.setUserId(userId);
        method.setName(name);
        method.setIcon(trimToNull(request.icon()));
        method.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        method.setPinned(Boolean.TRUE.equals(request.pinned()));
        return method;
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void evictAfterCreate(Long userId) {
        cacheInvalidationService.evictPaymentMethodsAfterCommit(userId);
        cacheInvalidationService.evictRecommendationsAfterCommit(userId);
    }

    private void evictAfterUpdateOrDelete(Long userId) {
        cacheInvalidationService.evictPaymentMethodsAfterCommit(userId);
        cacheInvalidationService.evictRecommendationsAfterCommit(userId);
        cacheInvalidationService.evictStatisticsAfterCommit(userId);
    }

    private void audit(Long userId, String action, Long targetId) {
        businessAuditLogService.recordSuccess(userId, action, "PAYMENT_METHOD", targetId, "USER");
    }
}
