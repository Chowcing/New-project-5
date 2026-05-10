package com.example.expense.budget.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.expense.budget.dto.BudgetRequest;
import com.example.expense.budget.entity.Budget;
import com.example.expense.budget.mapper.BudgetMapper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {
    @Mock
    private BudgetMapper budgetMapper;
    @Mock
    private CategoryService categoryService;

    @Test
    void createRejectsDuplicateMonthlyTotalBudget() {
        BudgetService service = new BudgetService(budgetMapper, categoryService);
        when(budgetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1001L,
                new BudgetRequest("2026-05", null, new BigDecimal("1000.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该月份的整月总预算已存在");

        verifyNoInteractions(categoryService);
        verify(budgetMapper, never()).insert(any(Budget.class));
    }

    @Test
    void updateRejectsDuplicateCategoryBudget() {
        BudgetService service = new BudgetService(budgetMapper, categoryService);
        Budget existing = new Budget();
        existing.setId(11L);
        existing.setUserId(1001L);
        existing.setMonth("2026-05");
        existing.setCategoryId(10L);
        when(budgetMapper.selectOne(any())).thenReturn(existing);
        when(categoryService.requireOwned(1001L, 20L)).thenReturn(new Category());
        when(budgetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1001L, 11L,
                new BudgetRequest("2026-05", 20L, new BigDecimal("300.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该月份的分类预算已存在");

        verify(categoryService).requireOwned(1001L, 20L);
        verify(budgetMapper, never()).updateById(any(Budget.class));
    }
}
