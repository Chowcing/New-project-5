package com.example.expense.category.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.dto.CategoryRequest;
import com.example.expense.category.entity.Category;
import com.example.expense.category.mapper.CategoryMapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.common.web.PageResponse;
import com.example.expense.common.init.DefaultDataSeeds;
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
public class CategoryService {
    private final CategoryMapper categoryMapper;
    private final TransactionMapper transactionMapper;
    private final RecurringRuleMapper recurringRuleMapper;
    private final CacheInvalidationService cacheInvalidationService;
    private final BusinessAuditLogService businessAuditLogService;

    public CategoryService(CategoryMapper categoryMapper, TransactionMapper transactionMapper, RecurringRuleMapper recurringRuleMapper) {
        this(categoryMapper, transactionMapper, recurringRuleMapper, null, null);
    }

    public CategoryService(
            CategoryMapper categoryMapper,
            TransactionMapper transactionMapper,
            RecurringRuleMapper recurringRuleMapper,
            CacheInvalidationService cacheInvalidationService
    ) {
        this(categoryMapper, transactionMapper, recurringRuleMapper, cacheInvalidationService, null);
    }

    @Autowired
    public CategoryService(
            CategoryMapper categoryMapper,
            TransactionMapper transactionMapper,
            RecurringRuleMapper recurringRuleMapper,
            CacheInvalidationService cacheInvalidationService,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.categoryMapper = categoryMapper;
        this.transactionMapper = transactionMapper;
        this.recurringRuleMapper = recurringRuleMapper;
        this.cacheInvalidationService = cacheInvalidationService;
        this.businessAuditLogService = businessAuditLogService;
    }

    @Cacheable(cacheNames = CacheNames.CATEGORIES, key = "T(com.example.expense.common.cache.CacheKeys).categoryList(#userId, #type)")
    public List<Category> list(Long userId, String type) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(type != null && !type.isBlank(), Category::getType, type == null ? null : type.trim())
                .orderByDesc(Category::getPinned)
                .orderByAsc(Category::getSortOrder)
                .orderByDesc(Category::getId));
    }

    public Category create(Long userId, CategoryRequest request) {
        String name = normalizeName(request.name());
        String type = request.type().trim();
        ensureNameAvailable(userId, type, name, null);
        Category category = toEntity(new Category(), userId, request, type, name);
        categoryMapper.insert(category);
        evictAfterCreate(userId);
        audit(userId, "CATEGORY_CREATE", category.getId());
        return category;
    }

    public void createDefaults(Long userId) {
        for (DefaultDataSeeds.CategorySeed seed : DefaultDataSeeds.CATEGORY_SEEDS) {
            createDefaultIfMissing(userId, seed);
        }
        evictAfterCreate(userId);
    }

    public Category update(Long userId, Long id, CategoryRequest request) {
        Category category = requireOwned(userId, id);
        String name = normalizeName(request.name());
        String type = request.type().trim();
        ensureReferencedTypeUnchanged(userId, category, type);
        ensureNameAvailable(userId, type, name, id);
        toEntity(category, userId, request, type, name);
        categoryMapper.updateById(category);
        evictAfterUpdateOrDelete(userId);
        audit(userId, "CATEGORY_UPDATE", id);
        return category;
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        long referenceCount = transactionMapper.countRecords(userId, null, null, null, null, id, null, null);
        long recurringReferenceCount = recurringRuleMapper.selectCount(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getCategoryId, id)
                .eq(RecurringRule::getDeleted, 0));
        long totalReferences = referenceCount + recurringReferenceCount;
        if (totalReferences > 0) {
            throw new IllegalArgumentException("分类已被 " + totalReferences + " 条记录或周期规则引用，不能删除");
        }
        categoryMapper.deleteById(id);
        evictAfterUpdateOrDelete(userId);
        audit(userId, "CATEGORY_DELETE", id);
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
        long recurringReferenceCount = recurringRuleMapper.selectCount(new LambdaQueryWrapper<RecurringRule>()
                .eq(RecurringRule::getUserId, userId)
                .eq(RecurringRule::getCategoryId, category.getId())
                .eq(RecurringRule::getDeleted, 0));
        long totalReferences = referenceCount + recurringReferenceCount;
        if (totalReferences > 0) {
            throw new IllegalArgumentException("分类已被 " + totalReferences + " 条记录或周期规则引用，不能修改类型");
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
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setPinned(Boolean.TRUE.equals(request.pinned()));
        return category;
    }

    private void createDefaultIfMissing(Long userId, DefaultDataSeeds.CategorySeed seed) {
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(Category::getType, seed.type())
                .eq(Category::getName, seed.name()));
        if (count != null && count > 0) {
            return;
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(seed.name());
        category.setType(seed.type());
        category.setIcon(seed.icon());
        category.setSortOrder(seed.sortOrder());
        category.setPinned(false);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ignored) {
            // 并发初始化时可能被其他线程先插入，忽略重复键即可保持幂等。
        }
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
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictCategoriesAfterCommit(userId);
            cacheInvalidationService.evictRecommendationsAfterCommit(userId);
        }
    }

    private void evictAfterUpdateOrDelete(Long userId) {
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictCategoriesAfterCommit(userId);
            cacheInvalidationService.evictRecommendationsAfterCommit(userId);
            cacheInvalidationService.evictStatisticsAfterCommit(userId);
        }
    }

    private void audit(Long userId, String action, Long targetId) {
        if (businessAuditLogService != null) {
            businessAuditLogService.recordSuccess(userId, action, "CATEGORY", targetId, "USER");
        }
    }
}
