package com.example.expense.admin.controller;

import com.example.expense.admin.dto.AdminAuditLogResponse;
import com.example.expense.admin.dto.AdminOverviewResponse;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.dto.AdminTransactionResponse;
import com.example.expense.admin.dto.AdminUserDetailResponse;
import com.example.expense.admin.dto.AdminUserResponse;
import com.example.expense.admin.dto.AdminUserStatusRequest;
import com.example.expense.admin.service.AdminService;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.common.web.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(adminService.listUsers(keyword, status, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserDetailResponse> user(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getUser(id));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserStatusRequest request
    ) {
        return ApiResponse.ok("用户状态已更新", adminService.updateUserStatus(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/users/{id}/revoke-tokens")
    public ApiResponse<Void> revokeUserTokens(
            @PathVariable Long id,
            @Valid @RequestBody AdminReasonRequest request
    ) {
        adminService.revokeUserTokens(SecurityUtils.currentUserId(), id, request);
        return ApiResponse.ok("登录凭证已吊销", null);
    }

    @PostMapping("/users/{id}/reset-email")
    public ApiResponse<Void> resetUserEmail(
            @PathVariable Long id,
            @Valid @RequestBody AdminReasonRequest request
    ) {
        adminService.resetUserEmail(SecurityUtils.currentUserId(), id, request);
        return ApiResponse.ok("邮箱验证状态已重置", null);
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<AdminTransactionResponse>> transactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(adminService.listTransactions(userId, type, startDate, endDate, channel, keyword, page, size));
    }

    @DeleteMapping("/transactions/{id}")
    public ApiResponse<Void> deleteTransaction(
            @PathVariable Long id,
            @Valid @RequestBody AdminReasonRequest request
    ) {
        adminService.deleteTransaction(SecurityUtils.currentUserId(), id, request);
        return ApiResponse.ok("记录已删除", null);
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminAuditLogResponse>> auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(adminService.listAuditLogs(page, size));
    }
}
