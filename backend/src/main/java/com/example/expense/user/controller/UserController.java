package com.example.expense.user.controller;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.user.dto.RecycleBinSettingsRequest;
import com.example.expense.user.dto.RecycleBinSettingsResponse;
import com.example.expense.user.dto.UserProfileResponse;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import com.example.expense.user.service.RecycleBinSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserMapper userMapper;
    private final AdminProperties adminProperties;
    private final RecycleBinSettingsService recycleBinSettingsService;

    public UserController(
            UserMapper userMapper,
            AdminProperties adminProperties,
            RecycleBinSettingsService recycleBinSettingsService) {
        this.userMapper = userMapper;
        this.adminProperties = adminProperties;
        this.recycleBinSettingsService = recycleBinSettingsService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        Long userId = SecurityUtils.currentUserId();
        ExpenseUser user = userMapper.selectById(userId);
        return ApiResponse.ok(new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                adminProperties.isAdmin(user.getUsername()),
                user.getEmail(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt()));
    }

    @GetMapping("/me/recycle-bin-settings")
    public ApiResponse<RecycleBinSettingsResponse> recycleBinSettings() {
        return ApiResponse.ok(recycleBinSettingsService.get(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me/recycle-bin-settings")
    public ApiResponse<RecycleBinSettingsResponse> updateRecycleBinSettings(
            @Valid @RequestBody RecycleBinSettingsRequest request) {
        return ApiResponse.ok(recycleBinSettingsService.update(SecurityUtils.currentUserId(), request));
    }
}
