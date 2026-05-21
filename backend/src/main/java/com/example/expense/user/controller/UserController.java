package com.example.expense.user.controller;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.user.dto.UserProfileResponse;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserMapper userMapper;
    private final AdminProperties adminProperties;

    public UserController(UserMapper userMapper, AdminProperties adminProperties) {
        this.userMapper = userMapper;
        this.adminProperties = adminProperties;
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
                user.getCreatedAt()));
    }
}
