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
                .eq(type != null && !type.isBlank(), Category::getType, type)
                .orderByAsc(Category::getSortOrder)
                .orderByDesc(Category::getId));
    }

    public Category create(Long userId, CategoryRequest request) {
        Category category = toEntity(new Category(), userId, request);
        categoryMapper.insert(category);
        return category;
    }

    public Category update(Long userId, Long id, CategoryRequest request) {
        Category category = requireOwned(userId, id);
        toEntity(category, userId, request);
        categoryMapper.updateById(category);
        return category;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        long referenceCount = transactionMapper.countRecords(userId, null, null, null, id, null, null);
        if (referenceCount > 0) {
            throw new IllegalArgumentException("分类已被 " + referenceCount + " 条记录引用，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    public PageResponse<TransactionResponse> references(Long userId, Long id, int size) {
        requireOwned(userId, id);
        long total = transactionMapper.countRecords(userId, null, null, null, id, null, null);
        List<TransactionResponse> records = transactionMapper.selectRecords(
                userId, null, null, null, id, null, null, size, 0L);
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

    private Category toEntity(Category category, Long userId, CategoryRequest request) {
        category.setUserId(userId);
        category.setName(request.name());
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return category;
    }
}
