package com.example.expense.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.dto.CategoryRequest;
import com.example.expense.category.entity.Category;
import com.example.expense.category.mapper.CategoryMapper;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;

    public CategoryService(CategoryMapper categoryMapper, TransactionMapper transactionMapper) {
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
    }

    public List<Category> list(Long userId, String type) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(type != null && !type.isBlank(), Category::getType, type == null ? null : type.trim())
                .orderByAsc(Category::getSortOrder)
                .orderByDesc(Category::getId));
    }

    public Category create(Long userId, CategoryRequest request) {
        String name = normalizeName(request.name());
        String type = request.type().trim();
        ensureNameAvailable(userId, type, name, null);
        Category category = toEntity(new Category(), userId, request, type, name);
        categoryMapper.insert(category);
        return category;
    }

    public void createDefaults(Long userId) {
        createDefault(userId, "餐饮", "EXPENSE", "shop-o", "#ee6a5c", 10);
        createDefault(userId, "交通", "EXPENSE", "logistics", "#4d8cff", 20);
        createDefault(userId, "购物", "EXPENSE", "cart-o", "#f0a23a", 30);
        createDefault(userId, "日用", "EXPENSE", "bag-o", "#2f7d68", 40);
        createDefault(userId, "住房", "EXPENSE", "home-o", "#8b5cf6", 50);
        createDefault(userId, "水电燃气", "EXPENSE", "fire-o", "#f59e0b", 60);
        createDefault(userId, "通讯", "EXPENSE", "phone-o", "#3b82f6", 70);
        createDefault(userId, "医疗", "EXPENSE", "shield-o", "#e25555", 80);
        createDefault(userId, "教育", "EXPENSE", "bookmark-o", "#64748b", 90);
        createDefault(userId, "娱乐", "EXPENSE", "music-o", "#d85f8a", 100);
        createDefault(userId, "旅行", "EXPENSE", "hotel-o", "#14b8a6", 110);
        createDefault(userId, "人情礼金", "EXPENSE", "gift-o", "#ec4899", 120);
        createDefault(userId, "其他支出", "EXPENSE", "records-o", "#64748b", 990);

        createDefault(userId, "工资", "INCOME", "paid", "#39a66a", 10);
        createDefault(userId, "奖金", "INCOME", "gold-coin-o", "#2f9b63", 20);
        createDefault(userId, "兼职", "INCOME", "manager-o", "#3b82f6", 30);
        createDefault(userId, "投资理财", "INCOME", "chart-trending-o", "#f59e0b", 40);
        createDefault(userId, "报销", "INCOME", "balance-list-o", "#8b5cf6", 50);
        createDefault(userId, "退款", "INCOME", "refund-o", "#2f7d68", 60);
        createDefault(userId, "其他收入", "INCOME", "cash-back-record", "#64748b", 990);
    }

    public Category update(Long userId, Long id, CategoryRequest request) {
        Category category = requireOwned(userId, id);
        String name = normalizeName(request.name());
        String type = request.type().trim();
        ensureReferencedTypeUnchanged(userId, category, type);
        ensureNameAvailable(userId, type, name, id);
        toEntity(category, userId, request, type, name);
        categoryMapper.updateById(category);
        return category;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        long referenceCount = transactionMapper.countRecords(userId, null, null, null, null, id, null, null);
        if (referenceCount > 0) {
            throw new IllegalArgumentException("分类已被 " + referenceCount + " 条记录引用，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    public PageResponse<TransactionResponse> references(Long userId, Long id, int size) {
        requireOwned(userId, id);
        long total = transactionMapper.countRecords(userId, null, null, null, null, id, null, null);
        List<TransactionResponse> records = transactionMapper.selectRecords(
                userId, null, null, null, null, id, null, null, size, 0L);
        return PageResponse.of(records, total, 1, size);
    }

    public Category requireOwned(Long userId, Long id) {
        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, id)
                .eq(Category::getUserId, userId));
        if (category == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        return category;
    }

    private void ensureReferencedTypeUnchanged(Long userId, Category category, String type) {
        if (category.getType() == null || category.getType().equals(type)) {
            return;
        }
        long referenceCount = transactionMapper.countRecords(userId, null, null, null, null, category.getId(), null, null);
        if (referenceCount > 0) {
            throw new IllegalArgumentException("分类已被 " + referenceCount + " 条记录引用，不能修改类型");
        }
    }

    private void ensureNameAvailable(Long userId, String type, String name, Long excludedId) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(Category::getType, type)
                .eq(Category::getName, name)
                .ne(excludedId != null, Category::getId, excludedId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException(("EXPENSE".equals(type) ? "支出" : "收入") + "分类已存在");
        }
    }

    private Category toEntity(Category category, Long userId, CategoryRequest request, String type, String name) {
        category.setUserId(userId);
        category.setName(name);
        category.setType(type);
        category.setIcon(trimToNull(request.icon()));
        category.setColor(trimToNull(request.color()));
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return category;
    }

    private void createDefault(Long userId, String name, String type, String icon, String color, int sortOrder) {
        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        category.setType(type);
        category.setIcon(icon);
        category.setColor(color);
        category.setSortOrder(sortOrder);
        categoryMapper.insert(category);
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
}
