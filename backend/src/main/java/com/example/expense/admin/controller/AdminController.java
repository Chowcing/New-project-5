package com.example.expense.admin.controller;

import com.example.expense.admin.dto.AdminAuditLogResponse;
import com.example.expense.admin.dto.AdminOverviewResponse;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.dto.AdminTransactionDetailResponse;
import com.example.expense.admin.dto.AdminTransactionResponse;
import com.example.expense.admin.dto.AdminWorkbenchResponse;
import com.example.expense.admin.dto.AdminUserDetailResponse;
import com.example.expense.admin.dto.AdminUserResponse;
import com.example.expense.admin.dto.AdminUserStatusRequest;
import com.example.expense.admin.service.AdminService;
import com.example.expense.businessaudit.dto.BusinessAuditLogResponse;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionImageContent;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final BusinessAuditLogService businessAuditLogService;

    public AdminController(AdminService adminService, BusinessAuditLogService businessAuditLogService) {
        this.adminService = adminService;
        this.businessAuditLogService = businessAuditLogService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/workbench")
    public ApiResponse<AdminWorkbenchResponse> workbench() {
        return ApiResponse.ok(adminService.workbench());
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

    @GetMapping("/transactions/{id}")
    public ApiResponse<AdminTransactionDetailResponse> transaction(@PathVariable Long id) {
        return ApiResponse.ok(adminService.getTransactionDetail(id));
    }

    @GetMapping("/transactions/{id}/images/{imageId}")
    public ResponseEntity<Resource> readTransactionImage(@PathVariable Long id, @PathVariable Long imageId) {
        TransactionImageContent content = adminService.readTransactionImage(id, imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(content.resource());
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
            @RequestParam(required = false) Long adminUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        return ApiResponse.ok(adminService.listAuditLogs(adminUserId, action, targetType, targetId, startAt, endAt, page, size));
    }

    @GetMapping("/business-audit-logs")
    public ApiResponse<PageResponse<BusinessAuditLogResponse>> businessAuditLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(businessAuditLogService.list(userId, action, targetType, source, page, size));
    }
}
