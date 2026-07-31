package com.example.expense.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.user.dto.RecycleBinSettingsRequest;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecycleBinSettingsServiceTest {
    private static final Long USER_ID = 1001L;

    @Mock
    private UserMapper userMapper;

    @Test
    void getReturnsPersistedRetentionDays() {
        RecycleBinSettingsService service = new RecycleBinSettingsService(userMapper);
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, 30));

        assertThat(service.get(USER_ID).retentionDays()).isEqualTo(30);
    }

    @Test
    void getUsesThirtyDaysForLegacyNullRetentionDays() {
        RecycleBinSettingsService service = new RecycleBinSettingsService(userMapper);
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, null));

        assertThat(service.get(USER_ID).retentionDays()).isEqualTo(30);
    }

    @Test
    void updatePersistsValidatedRetentionDays() {
        RecycleBinSettingsService service = new RecycleBinSettingsService(userMapper);
        ExpenseUser user = user(USER_ID, 30);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(1);

        assertThat(service.update(USER_ID, new RecycleBinSettingsRequest(15)).retentionDays())
                .isEqualTo(15);
        assertThat(user.getTrashRetentionDays()).isEqualTo(15);
        verify(userMapper).updateById(user);
    }

    @Test
    void updateRejectsWhenUserDisappearsBeforePersistence() {
        RecycleBinSettingsService service = new RecycleBinSettingsService(userMapper);
        ExpenseUser user = user(USER_ID, 30);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.updateById(user)).thenReturn(0);

        assertThatThrownBy(() -> service.update(USER_ID, new RecycleBinSettingsRequest(15)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    @Test
    void getRejectsMissingUser() {
        RecycleBinSettingsService service = new RecycleBinSettingsService(userMapper);

        assertThatThrownBy(() -> service.get(USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");
    }

    private ExpenseUser user(Long id, Integer retentionDays) {
        ExpenseUser user = new ExpenseUser();
        user.setId(id);
        user.setTrashRetentionDays(retentionDays);
        return user;
    }
}
