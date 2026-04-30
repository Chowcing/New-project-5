package com.example.expense.transaction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.account.service.AccountService;
import com.example.expense.category.service.CategoryService;
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
    private final AccountService accountService;

    public TransactionService(
            TransactionMapper transactionMapper,
            CategoryService categoryService,
            AccountService accountService
    ) {
        this.transactionMapper = transactionMapper;
        this.categoryService = categoryService;
        this.accountService = accountService;
    }

    public List<TransactionResponse> list(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            Long categoryId,
            Long accountId,
            String keyword
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        // 所有列表和导出查询统一从 Mapper 注入 userId 条件，避免前端传参造成跨用户读取。
        return transactionMapper.selectRecords(userId, type, startAt, endAt, categoryId, accountId, keyword);
    }

    public ExpenseTransaction create(Long userId, TransactionRequest request) {
        ensureOwnedReferences(userId, request);
        ExpenseTransaction transaction = toEntity(new ExpenseTransaction(), userId, request);
        transactionMapper.insert(transaction);
        return transaction;
    }

    public ExpenseTransaction update(Long userId, Long id, TransactionRequest request) {
        ExpenseTransaction transaction = requireOwned(userId, id);
        ensureOwnedReferences(userId, request);
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
        accountService.requireOwned(userId, request.accountId());
    }

    private ExpenseTransaction toEntity(ExpenseTransaction transaction, Long userId, TransactionRequest request) {
        transaction.setUserId(userId);
        transaction.setType(request.type());
        transaction.setAmount(request.amount());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setCategoryId(request.categoryId());
        transaction.setAccountId(request.accountId());
        transaction.setNote(request.note());
        return transaction;
    }
}

