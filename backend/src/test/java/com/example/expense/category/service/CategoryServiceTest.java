package com.example.expense.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.category.dto.CategoryRequest;
import com.example.expense.category.entity.Category;
import com.example.expense.category.mapper.CategoryMapper;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private RecurringRuleMapper recurringRuleMapper;

    @Test
    void createRejectsDuplicateNameWithinSameType() {
        CategoryService service = new CategoryService(categoryMapper, transactionMapper, recurringRuleMapper);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1001L,
                new CategoryRequest(" 餐饮 ", "EXPENSE", "shop-o", "#2f7d68", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("支出分类已存在");

        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    void updateRejectsDuplicateNameWithinSameType() {
        CategoryService service = new CategoryService(categoryMapper, transactionMapper, recurringRuleMapper);
        Category existing = new Category();
        existing.setId(11L);
        existing.setUserId(1001L);
        existing.setName("交通");
        existing.setType("EXPENSE");
        when(categoryMapper.selectOne(any())).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1001L, 11L,
                new CategoryRequest("餐饮", "EXPENSE", "shop-o", "#2f7d68", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("支出分类已存在");

        verify(categoryMapper, never()).updateById(any(Category.class));
    }

    @Test
    void updateRejectsTypeChangeWhenCategoryHasReferences() {
        CategoryService service = new CategoryService(categoryMapper, transactionMapper, recurringRuleMapper);
        Category existing = new Category();
        existing.setId(11L);
        existing.setUserId(1001L);
        existing.setName("餐饮");
        existing.setType("EXPENSE");
        when(categoryMapper.selectOne(any())).thenReturn(existing);
        when(transactionMapper.countRecords(1001L, null, null, null, null, 11L, null, null)).thenReturn(2L);
        when(recurringRuleMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.update(1001L, 11L,
                new CategoryRequest("餐饮", "INCOME", "shop-o", "#2f7d68", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分类已被 2 条记录或周期规则引用，不能修改类型");

        verify(categoryMapper, never()).updateById(any(Category.class));
    }

    @Test
    void createDefaultsCreatesCommonCategories() {
        CategoryService service = new CategoryService(categoryMapper, transactionMapper, recurringRuleMapper);

        service.createDefaults(1001L);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper, times(20)).insert(categoryCaptor.capture());
        List<Category> categories = categoryCaptor.getAllValues();
        assertThat(categories).extracting(Category::getName).containsExactly(
                "餐饮", "交通", "购物", "日用", "住房", "水电燃气", "通讯", "医疗", "教育", "娱乐", "旅行", "人情礼金", "其他支出",
                "工资", "奖金", "兼职", "投资理财", "报销", "退款", "其他收入"
        );
        assertThat(categories).extracting(Category::getUserId).containsOnly(1001L);
        assertThat(categories).extracting(Category::getName).doesNotContain("零食");
    }

    @Test
    void createDefaultsSkipsExistingCategories() {
        CategoryService service = new CategoryService(categoryMapper, transactionMapper, recurringRuleMapper);
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        service.createDefaults(1001L);

        verify(categoryMapper, never()).insert(any(Category.class));
    }
}
