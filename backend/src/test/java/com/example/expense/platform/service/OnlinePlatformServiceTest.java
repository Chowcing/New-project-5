package com.example.expense.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void createRejectsDuplicateName() {
        OnlinePlatformService service = new OnlinePlatformService(onlinePlatformMapper, transactionMapper);
        when(onlinePlatformMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1001L, new OnlinePlatformRequest(" 淘宝 ", "shop-o", 10, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线上平台已存在");

        verify(onlinePlatformMapper, never()).insert(any(OnlinePlatform.class));
    }

    @Test
    void createPersistsNormalizedPlatform() {
        OnlinePlatformService service = new OnlinePlatformService(onlinePlatformMapper, transactionMapper);
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
        OnlinePlatformService service = new OnlinePlatformService(onlinePlatformMapper, transactionMapper);

        service.createDefaults(1001L);

        ArgumentCaptor<OnlinePlatform> captor = ArgumentCaptor.forClass(OnlinePlatform.class);
        verify(onlinePlatformMapper, org.mockito.Mockito.times(17)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(OnlinePlatform::getName)
                .contains("淘宝", "美团", "铁路12306", "微信", "支付宝", "饿了么", "百度地图");
        assertThat(captor.getAllValues()).extracting(OnlinePlatform::getUserId).containsOnly(1001L);
    }

    @Test
    void deleteRejectsReferencedPlatform() {
        OnlinePlatformService service = new OnlinePlatformService(onlinePlatformMapper, transactionMapper);
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(11L);
        platform.setUserId(1001L);
        platform.setName("淘宝");
        when(onlinePlatformMapper.selectOne(any())).thenReturn(platform);
        when(transactionMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1001L, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线上平台已被 2 条记录引用，不能删除");

        verify(onlinePlatformMapper, never()).deleteById(11L);
    }
}
