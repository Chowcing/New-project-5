package com.example.expense.auth.controller;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.auth.dto.LoginRequest;
import com.example.expense.auth.dto.RefreshTokenRequest;
import com.example.expense.auth.dto.RegisterRequest;
import com.example.expense.auth.dto.TokenResponse;
import com.example.expense.auth.service.AuthService;
import com.example.expense.auth.service.LoginRateLimiter;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.user.dto.UserProfileResponse;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
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
    private final AdminProperties adminProperties;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(
            AuthService authService,
            UserMapper userMapper,
            AdminProperties adminProperties,
            LoginRateLimiter loginRateLimiter
    ) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.adminProperties = adminProperties;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        loginRateLimiter.checkAllowed(request.username(), clientIp);
        try {
            TokenResponse tokenResponse = authService.login(request);
            loginRateLimiter.recordSuccess(request.username(), clientIp);
            return ApiResponse.ok("登录成功", tokenResponse);
        } catch (BadCredentialsException ex) {
            loginRateLimiter.recordFailure(request.username(), clientIp);
            throw ex;
        }
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
        return ApiResponse.ok(new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getStatus(),
                adminProperties.isAdmin(user.getUsername()),
                user.getCreatedAt()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            return commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
