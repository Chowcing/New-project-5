package com.example.expense.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.platform.dto.OnlinePlatformRequest;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.mapper.OnlinePlatformMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnlinePlatformServiceTest {
    @Mock
    private OnlinePlatformMapper onlinePlatformMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private CacheInvalidationService cacheInvalidationService;
    @Mock
    private BusinessAuditLogService businessAuditLogService;

    @Test
    void createRejectsDuplicateName() {
        OnlinePlatformService service = service();
        when(onlinePlatformMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1001L, new OnlinePlatformRequest(" 淘宝 ", "shop-o", 10, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线上平台已存在");

        verify(onlinePlatformMapper, never()).insert(any(OnlinePlatform.class));
    }

    @Test
    void createPersistsNormalizedPlatform() {
        OnlinePlatformService service = service();
        when(onlinePlatformMapper.selectCount(any())).thenReturn(0L);

        service.create(1001L, new OnlinePlatformRequest(" 淘宝 ", "shop-o", 10, true));

        ArgumentCaptor<OnlinePlatform> captor = ArgumentCaptor.forClass(OnlinePlatform.class);
        verify(onlinePlatformMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(captor.getValue().getName()).isEqualTo("淘宝");
        assertThat(captor.getValue().getIcon()).isEqualTo("shop-o");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(10);
        assertThat(captor.getValue().getPinned()).isTrue();
    }

    @Test
    void createDefaultsCreatesBuiltInPlatforms() {
        OnlinePlatformService service = service();

        service.createDefaults(1001L);

        ArgumentCaptor<OnlinePlatform> captor = ArgumentCaptor.forClass(OnlinePlatform.class);
        verify(onlinePlatformMapper, org.mockito.Mockito.times(17)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(OnlinePlatform::getName)
                .contains("淘宝", "美团", "铁路12306", "微信", "支付宝", "饿了么", "百度地图");
        assertThat(captor.getAllValues()).extracting(OnlinePlatform::getUserId).containsOnly(1001L);
    }

    @Test
    void deleteRejectsReferencedPlatform() {
        OnlinePlatformService service = service();
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(11L);
        platform.setUserId(1001L);
        platform.setName("淘宝");
        when(onlinePlatformMapper.selectOwnedForUpdate(1001L, 11L))
                .thenReturn(platform);
        when(transactionMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1001L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线上平台已被 2 条记录引用，不能删除");

        verify(onlinePlatformMapper, never()).deleteById(11L);
    }

    @Test
    void createInvalidatesReferenceAndAiSceneCaches() {
        OnlinePlatformService service = service();

        service.create(1001L, new OnlinePlatformRequest("淘宝", "shop-o", 10, true));

        verify(cacheInvalidationService).evictOnlinePlatformsAfterCommit(1001L);
        verify(cacheInvalidationService).evictAiSceneAfterCommit(1001L);
    }

    @Test
    void updateInvalidatesReferenceAndAiSceneCaches() {
        OnlinePlatformService service = service();
        OnlinePlatform platform = platform(11L, 1001L, "淘宝");
        when(onlinePlatformMapper.selectOne(any())).thenReturn(platform);

        service.update(1001L, 11L,
                new OnlinePlatformRequest("天猫", "shop-o", 20, true));

        verify(cacheInvalidationService).evictOnlinePlatformsAfterCommit(1001L);
        verify(cacheInvalidationService).evictAiSceneAfterCommit(1001L);
    }

    @Test
    void deleteInvalidatesReferenceAndAiSceneCaches() {
        OnlinePlatformService service = service();
        when(onlinePlatformMapper.selectOwnedForUpdate(1001L, 11L))
                .thenReturn(platform(11L, 1001L, "淘宝"));

        service.delete(1001L, 11L);

        verify(cacheInvalidationService).evictOnlinePlatformsAfterCommit(1001L);
        verify(cacheInvalidationService).evictAiSceneAfterCommit(1001L);
    }

    @Test
    void createDefaultsInvalidatesReferenceAndAiSceneCaches() {
        OnlinePlatformService service = service();

        service.createDefaults(1001L);

        verify(cacheInvalidationService).evictOnlinePlatformsAfterCommit(1001L);
        verify(cacheInvalidationService).evictAiSceneAfterCommit(1001L);
    }

    private OnlinePlatform platform(Long id, Long userId, String name) {
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(id);
        platform.setUserId(userId);
        platform.setName(name);
        return platform;
    }

    private OnlinePlatformService service() {
        return new OnlinePlatformService(
                onlinePlatformMapper,
                transactionMapper,
                cacheInvalidationService,
                businessAuditLogService);
    }
}
