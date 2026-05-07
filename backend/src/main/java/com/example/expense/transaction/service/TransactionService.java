package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {
    private final TransactionMapper transactionMapper;
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            PaymentMethodService paymentMethodService
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
    }

    public List<TransactionResponse> list(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        // 所有列表和导出查询统一从 Mapper 注入 userId 条件，避免前端传参造成跨用户读取。
        return transactionMapper.selectRecords(userId, type, startAt, endAt, categoryId, keyword);
    }

    public ExpenseTransaction create(Long userId, TransactionRequest request) {
        ensureOwnedReferences(userId, request);
        validateContext(request);
        ExpenseTransaction transaction = toEntity(new ExpenseTransaction(), userId, request);
        transactionMapper.insert(transaction);
        return transaction;
    }

    public ExpenseTransaction update(Long userId, Long id, TransactionRequest request) {
        ExpenseTransaction transaction = requireOwned(userId, id);
        ensureOwnedReferences(userId, request);
        validateContext(request);
        toEntity(transaction, userId, request);
        transactionMapper.updateById(transaction);
        return transaction;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        transactionMapper.deleteById(id);
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
    }

    private void validateContext(TransactionRequest request) {
        // 线上/线下的上下文字段不同，这里统一收口校验，避免前端绕过表单后写入半结构化脏数据。
        if ("OFFLINE".equals(request.channel()) && isBlank(request.offlinePlace())) {
            throw new IllegalArgumentException("线下记录需要填写地点");
        }
        if ("ONLINE".equals(request.channel()) && "EXPENSE".equals(request.type()) && isBlank(request.onlineApp())) {
            throw new IllegalArgumentException("线上支出需要填写消费 APP");
        }
    }

    private ExpenseTransaction toEntity(ExpenseTransaction transaction, Long userId, TransactionRequest request) {
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setItemName(request.itemName().trim());
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setChannel(request.channel());
        transaction.setOnlineApp("ONLINE".equals(request.channel()) ? trimToNull(request.onlineApp()) : null);
        transaction.setOfflinePlace("OFFLINE".equals(request.channel()) ? trimToNull(request.offlinePlace()) : null);
        PaymentMethod paymentMethod = paymentMethodService.requireOwned(userId, request.paymentMethodId());
        transaction.setPaymentMethodId(paymentMethod.getId());
        transaction.setPaymentMethodName(paymentMethod.getName());
        transaction.setCategoryId(request.categoryId());
        transaction.setNote(trimToNull(request.note()));
        return transaction;
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
}
