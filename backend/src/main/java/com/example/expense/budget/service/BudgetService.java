package com.example.expense.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.budget.dto.BudgetRequest;
import com.example.expense.budget.entity.Budget;
import com.example.expense.budget.mapper.BudgetMapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BudgetService {
    private final BudgetMapper budgetMapper;
    private final CategoryService categoryService;
    private final CacheInvalidationService cacheInvalidationService;

    public BudgetService(BudgetMapper budgetMapper, CategoryService categoryService) {
        this(budgetMapper, categoryService, null);
    }

    @Autowired
    public BudgetService(
            BudgetMapper budgetMapper,
            CategoryService categoryService,
            CacheInvalidationService cacheInvalidationService
    ) {
        this.budgetMapper = budgetMapper;
        this.categoryService = categoryService;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    public List<Budget> list(Long userId, String month) {
        return budgetMapper.selectList(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(month != null && !month.isBlank(), Budget::getMonth, month)
                .orderByDesc(Budget::getMonth)
                .orderByDesc(Budget::getId));
    }

    public Budget create(Long userId, BudgetRequest request) {
        String month = normalizeMonth(request.month());
        Long categoryId = request.categoryId();
        ensureOwnedCategory(userId, categoryId);
        ensureBudgetAvailable(userId, month, categoryId, null);
        Budget budget = toEntity(new Budget(), userId, request, month, categoryId);
        budgetMapper.insert(budget);
        evictStatistics(userId);
        return budget;
    }

    public Budget update(Long userId, Long id, BudgetRequest request) {
        Budget budget = requireOwned(userId, id);
        String month = normalizeMonth(request.month());
        Long categoryId = request.categoryId();
        ensureOwnedCategory(userId, categoryId);
        ensureBudgetAvailable(userId, month, categoryId, id);
        toEntity(budget, userId, request, month, categoryId);
        budgetMapper.updateById(budget);
        evictStatistics(userId);
        return budget;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        budgetMapper.deleteById(id);
        evictStatistics(userId);
    }

    private Budget requireOwned(Long userId, Long id) {
        Budget budget = budgetMapper.selectOne(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getId, id)
                .eq(Budget::getUserId, userId));
        if (budget == null) {
            throw new IllegalArgumentException("预算不存在");
        }
        return budget;
    }

    private void ensureOwnedCategory(Long userId, Long categoryId) {
        if (categoryId != null) {
            categoryService.requireOwned(userId, categoryId);
        }
    }

    private void ensureBudgetAvailable(Long userId, String month, Long categoryId, Long excludedId) {
        LambdaQueryWrapper<Budget> wrapper = new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(Budget::getMonth, month)
                .ne(excludedId != null, Budget::getId, excludedId);
        if (categoryId == null) {
            wrapper.isNull(Budget::getCategoryId);
        } else {
            wrapper.eq(Budget::getCategoryId, categoryId);
        }
        Long count = budgetMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException(categoryId == null ? "该月份的整月总预算已存在" : "该月份的分类预算已存在");
        }
    }

    private Budget toEntity(Budget budget, Long userId, BudgetRequest request, String month, Long categoryId) {
        budget.setUserId(userId);
        budget.setMonth(month);
        budget.setCategoryId(categoryId);
        budget.setAmount(request.amount());
        return budget;
    }

    private String normalizeMonth(String month) {
        return month.trim();
    }

    private void evictStatistics(Long userId) {
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictStatisticsAfterCommit(userId);
        }
    }
}
