package com.example.expense.user.service;

import com.example.expense.user.dto.RecycleBinSettingsRequest;
import com.example.expense.user.dto.RecycleBinSettingsResponse;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class RecycleBinSettingsService {
    private static final int DEFAULT_RETENTION_DAYS = 30;

    private final UserMapper userMapper;

    public RecycleBinSettingsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public RecycleBinSettingsResponse get(Long userId) {
        return response(requireUser(userId));
    }

    public RecycleBinSettingsResponse update(Long userId, RecycleBinSettingsRequest request) {
        ExpenseUser user = requireUser(userId);
        user.setTrashRetentionDays(request.retentionDays());
        if (userMapper.updateById(user) != 1) {
            throw new IllegalArgumentException("用户不存在");
        }
        return response(user);
    }

    private ExpenseUser requireUser(Long userId) {
        ExpenseUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private RecycleBinSettingsResponse response(ExpenseUser user) {
        Integer retentionDays = user.getTrashRetentionDays();
        return new RecycleBinSettingsResponse(retentionDays == null ? DEFAULT_RETENTION_DAYS : retentionDays);
    }
}
