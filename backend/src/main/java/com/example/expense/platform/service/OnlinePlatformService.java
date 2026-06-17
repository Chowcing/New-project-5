package com.example.expense.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.cache.CacheNames;
import com.example.expense.common.init.DefaultDataSeeds;
import com.example.expense.platform.dto.OnlinePlatformRequest;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.mapper.OnlinePlatformMapper;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class OnlinePlatformService {
    private final OnlinePlatformMapper onlinePlatformMapper;
    private final TransactionMapper transactionMapper;
    private final CacheInvalidationService cacheInvalidationService;
    private final BusinessAuditLogService businessAuditLogService;

    public OnlinePlatformService(OnlinePlatformMapper onlinePlatformMapper, TransactionMapper transactionMapper) {
        this(onlinePlatformMapper, transactionMapper, null, null);
    }

    public OnlinePlatformService(
            OnlinePlatformMapper onlinePlatformMapper,
            TransactionMapper transactionMapper,
            CacheInvalidationService cacheInvalidationService
    ) {
        this(onlinePlatformMapper, transactionMapper, cacheInvalidationService, null);
    }

    @Autowired
    public OnlinePlatformService(
            OnlinePlatformMapper onlinePlatformMapper,
            TransactionMapper transactionMapper,
            CacheInvalidationService cacheInvalidationService,
            BusinessAuditLogService businessAuditLogService
    ) {
        this.onlinePlatformMapper = onlinePlatformMapper;
        this.transactionMapper = transactionMapper;
        this.cacheInvalidationService = cacheInvalidationService;
        this.businessAuditLogService = businessAuditLogService;
    }

    @Cacheable(cacheNames = CacheNames.ONLINE_PLATFORMS, key = "T(com.example.expense.common.cache.CacheKeys).onlinePlatformList(#userId)")
    public List<OnlinePlatform> list(Long userId) {
        return onlinePlatformMapper.selectList(new LambdaQueryWrapper<OnlinePlatform>()
                .eq(OnlinePlatform::getUserId, userId)
                .orderByDesc(OnlinePlatform::getPinned)
                .orderByAsc(OnlinePlatform::getSortOrder)
                .orderByDesc(OnlinePlatform::getId));
    }

    public OnlinePlatform create(Long userId, OnlinePlatformRequest request) {
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, null);
        OnlinePlatform platform = toEntity(new OnlinePlatform(), userId, request, name);
        onlinePlatformMapper.insert(platform);
        evictAfterChange(userId);
        audit(userId, "ONLINE_PLATFORM_CREATE", platform.getId());
        return platform;
    }

    public OnlinePlatform update(Long userId, Long id, OnlinePlatformRequest request) {
        OnlinePlatform platform = requireOwned(userId, id);
        String name = normalizeName(request.name());
        ensureNameAvailable(userId, name, id);
        toEntity(platform, userId, request, name);
        onlinePlatformMapper.updateById(platform);
        evictAfterChange(userId);
        audit(userId, "ONLINE_PLATFORM_UPDATE", id);
        return platform;
    }

    public void delete(Long userId, Long id) {
        long referenceCount = referenceCount(userId, id);
        if (referenceCount > 0) {
            throw new IllegalArgumentException("线上平台已被 " + referenceCount + " 条记录引用，不能删除");
        }
        onlinePlatformMapper.deleteById(id);
        evictAfterChange(userId);
        audit(userId, "ONLINE_PLATFORM_DELETE", id);
    }

    public long referenceCount(Long userId, Long id) {
        requireOwned(userId, id);
        return transactionMapper.selectCount(new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getUserId, userId)
                .eq(ExpenseTransaction::getOnlinePlatformId, id));
    }

    public OnlinePlatform requireOwned(Long userId, Long id) {
        OnlinePlatform platform = onlinePlatformMapper.selectOne(new LambdaQueryWrapper<OnlinePlatform>()
                .eq(OnlinePlatform::getId, id)
                .eq(OnlinePlatform::getUserId, userId));
        if (platform == null) {
            throw new IllegalArgumentException("线上平台不存在");
        }
        return platform;
    }

    public void createDefaults(Long userId) {
        for (DefaultDataSeeds.OnlinePlatformSeed seed : DefaultDataSeeds.ONLINE_PLATFORM_SEEDS) {
            createDefaultIfMissing(userId, seed);
        }
        evictAfterChange(userId);
    }

    private void createDefaultIfMissing(Long userId, DefaultDataSeeds.OnlinePlatformSeed seed) {
        Long count = onlinePlatformMapper.selectCount(new LambdaQueryWrapper<OnlinePlatform>()
                .eq(OnlinePlatform::getUserId, userId)
                .eq(OnlinePlatform::getName, seed.name()));
        if (count != null && count > 0) {
            return;
        }

        OnlinePlatform platform = new OnlinePlatform();
        platform.setUserId(userId);
        platform.setName(seed.name());
        platform.setIcon(seed.icon());
        platform.setSortOrder(seed.sortOrder());
        platform.setPinned(false);
        try {
            onlinePlatformMapper.insert(platform);
        } catch (DuplicateKeyException ignored) {
            // 并发初始化时可能被其他线程先插入，忽略重复键即可保持幂等。
        }
    }

    private void ensureNameAvailable(Long userId, String name, Long excludedId) {
        Long count = onlinePlatformMapper.selectCount(new LambdaQueryWrapper<OnlinePlatform>()
                .eq(OnlinePlatform::getUserId, userId)
                .eq(OnlinePlatform::getName, name)
                .ne(excludedId != null, OnlinePlatform::getId, excludedId));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("线上平台已存在");
        }
    }

    private OnlinePlatform toEntity(OnlinePlatform platform, Long userId, OnlinePlatformRequest request, String name) {
        platform.setUserId(userId);
        platform.setName(name);
        platform.setIcon(trimToNull(request.icon()));
        platform.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        platform.setPinned(Boolean.TRUE.equals(request.pinned()));
        return platform;
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

    private void evictAfterChange(Long userId) {
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictOnlinePlatformsAfterCommit(userId);
            cacheInvalidationService.evictRecommendationsAfterCommit(userId);
        }
    }

    private void audit(Long userId, String action, Long targetId) {
        if (businessAuditLogService != null) {
            businessAuditLogService.recordSuccess(userId, action, "ONLINE_PLATFORM", targetId, "USER");
        }
    }
}
