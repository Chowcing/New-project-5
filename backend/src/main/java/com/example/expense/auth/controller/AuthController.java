package com.example.expense.auth.controller;

import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RefreshTokenRequest;
import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.service.AuthService;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.user.dto.UserProfileResponse;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("登录成功", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.ok("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        ExpenseUser user = userMapper.selectById(SecurityUtils.currentUserId());
        return ApiResponse.ok(new UserProfileResponse(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt()));
    }
}

