package com.example.expense.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.expense.admin.config.AdminProperties;
import com.example.expense.admin.dto.AdminAuditLogResponse;
import com.example.expense.admin.dto.AdminOverviewResponse;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.dto.AdminTransactionResponse;
import com.example.expense.admin.dto.AdminUserDetailResponse;
import com.example.expense.admin.dto.AdminUserResponse;
import com.example.expense.admin.dto.AdminUserStatisticsResponse;
import com.example.expense.admin.dto.AdminUserStatusRequest;
import com.example.expense.admin.entity.AdminAuditLog;
import com.example.expense.admin.mapper.AdminAuditLogMapper;
import com.example.expense.admin.mapper.AdminMapper;
import com.example.expense.auth.service.AuthService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.transaction.service.TransactionService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final AdminMapper adminMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final TransactionMapper transactionMapper;
    private final TransactionService transactionService;
    private final AdminAuditLogMapper adminAuditLogMapper;
    private final AdminProperties adminProperties;
    private final Clock clock;

    public AdminService(
            AdminMapper adminMapper,
            UserMapper userMapper,
            AuthService authService,
            TransactionMapper transactionMapper,
            TransactionService transactionService,
            AdminAuditLogMapper adminAuditLogMapper,
            AdminProperties adminProperties,
            Clock clock
    ) {
        this.adminMapper = adminMapper;
        this.userMapper = userMapper;
        this.authService = authService;
        this.transactionMapper = transactionMapper;
        this.transactionService = transactionService;
        this.adminAuditLogMapper = adminAuditLogMapper;
        this.adminProperties = adminProperties;
        this.clock = clock;
    }

    public AdminOverviewResponse overview() {
        LocalDate startDate = LocalDate.now(clock).minusDays(29);
        LocalDateTime since = startDate.atStartOfDay();
        return new AdminOverviewResponse(
                adminMapper.countUsers(null, null),
                adminMapper.countDisabledUsers(),
                adminMapper.countActiveUsersSince(since),
                adminMapper.countAllTransactions(),
                money(adminMapper.sumTransactions("EXPENSE")),
                money(adminMapper.sumTransactions("INCOME")),
                adminMapper.selectDailyMetrics(startDate)
        );
    }

    public PageResponse<AdminUserResponse> listUsers(String keyword, String status, int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        long total = adminMapper.countUsers(blankToNull(keyword), blankToNull(status));
        List<AdminUserResponse> rows = adminMapper.selectUsers(
                blankToNull(keyword),
                blankToNull(status),
                safeSize,
                (long) (safePage - 1) * safeSize);
        rows.forEach(this::markAdmin);
        return PageResponse.of(rows, total, safePage, safeSize);
    }

    public AdminUserDetailResponse getUser(Long id) {
        AdminUserResponse user = adminMapper.selectUser(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        markAdmin(user);
        AdminUserStatisticsResponse statistics = adminMapper.selectUserStatistics(id);
        normalizeStatistics(statistics);
        return new AdminUserDetailResponse(user, statistics);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long adminUserId, Long targetUserId, AdminUserStatusRequest request) {
        ExpenseUser user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String status = normalizeStatus(request.status());
        if (adminUserId.equals(targetUserId) && "DISABLED".equals(status)) {
            throw new IllegalArgumentException("不能禁用当前管理员账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if ("DISABLED".equals(status)) {
            revokeTokens(targetUserId);
        }
        audit(adminUserId, "USER_STATUS_" + status, "USER", targetUserId, request.reason());
        return getUser(targetUserId).user();
    }

    @Transactional
    public void revokeUserTokens(Long adminUserId, Long targetUserId, AdminReasonRequest request) {
        if (userMapper.selectById(targetUserId) == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        revokeTokens(targetUserId);
        audit(adminUserId, "REVOKE_TOKENS", "USER", targetUserId, request.reason());
    }

    @Transactional
    public void resetUserEmail(Long adminUserId, Long targetUserId, AdminReasonRequest request) {
        ExpenseUser user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.update(null, new UpdateWrapper<ExpenseUser>()
                .eq("id", targetUserId)
                .set("email", null)
                .set("email_verified_at", null));
        revokeTokens(targetUserId);
        audit(adminUserId, "RESET_USER_EMAIL", "USER", targetUserId, request.reason());
    }

    public PageResponse<AdminTransactionResponse> listTransactions(
            Long userId,
            String type,
            LocalDate startDate,
            LocalDate endDate,
            String channel,
            String keyword,
            int page,
            int size
    ) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        LocalDateTime startAt = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endAt = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        long total = adminMapper.countTransactions(userId, blankToNull(type), startAt, endAt, blankToNull(channel), blankToNull(keyword));
        List<AdminTransactionResponse> rows = adminMapper.selectTransactions(
                userId,
                blankToNull(type),
                startAt,
                endAt,
                blankToNull(channel),
                blankToNull(keyword),
                safeSize,
                (long) (safePage - 1) * safeSize);
        return PageResponse.of(rows, total, safePage, safeSize);
    }

    @Transactional
    public void deleteTransaction(Long adminUserId, Long id, AdminReasonRequest request) {
        ExpenseTransaction transaction = transactionMapper.selectOne(new LambdaQueryWrapper<ExpenseTransaction>()
                .eq(ExpenseTransaction::getId, id));
        if (transaction == null) {
            throw new IllegalArgumentException("记录不存在");
        }
        transactionService.delete(transaction.getUserId(), id);
        audit(adminUserId, "TRANSACTION_DELETE", "TRANSACTION", id, request.reason());
    }

    public PageResponse<AdminAuditLogResponse> listAuditLogs(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);
        long total = adminMapper.countAuditLogs();
        List<AdminAuditLogResponse> rows = adminMapper.selectAuditLogs(safeSize, (long) (safePage - 1) * safeSize);
        return PageResponse.of(rows, total, safePage, safeSize);
    }

    private void revokeTokens(Long userId) {
        authService.revokeTokens(userId);
    }

    private void audit(Long adminUserId, String action, String targetType, Long targetId, String reason) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminUserId(adminUserId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setReason(trimReason(reason));
        adminAuditLogMapper.insert(log);
    }

    private void markAdmin(AdminUserResponse user) {
        user.setAdmin(adminProperties.isAdmin(user.getUsername()));
    }

    private void normalizeStatistics(AdminUserStatisticsResponse statistics) {
        statistics.setTotalExpense(money(statistics.getTotalExpense()));
        statistics.setTotalIncome(money(statistics.getTotalIncome()));
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase();
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw new IllegalArgumentException("用户状态无效");
        }
        return value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private int safePage(int page) {
        return Math.max(page, 1);
    }

    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
