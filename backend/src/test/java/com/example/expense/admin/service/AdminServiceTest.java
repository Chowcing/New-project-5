package com.example.expense.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.admin.dto.AdminAttentionItemResponse;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.dto.AdminTransactionDetailResponse;
import com.example.expense.admin.dto.AdminTransactionResponse;
import com.example.expense.admin.dto.AdminWorkbenchResponse;
import com.example.expense.admin.dto.AdminUserStatusRequest;
import com.example.expense.admin.entity.AdminAuditLog;
import com.example.expense.admin.mapper.AdminAuditLogMapper;
import com.example.expense.admin.mapper.AdminMapper;
import com.example.expense.auth.service.AuthService;
import com.example.expense.transaction.dto.TransactionImageResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.transaction.service.TransactionImageService;
import com.example.expense.transaction.service.TransactionService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    private static final Long ADMIN_USER_ID = 1L;
    private static final Long TARGET_USER_ID = 2L;
    private static final Long TRANSACTION_ID = 88L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuthService authService;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private TransactionImageService transactionImageService;
    @Mock
    private AdminAuditLogMapper adminAuditLogMapper;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                adminMapper,
                userMapper,
                authService,
                transactionMapper,
                transactionService,
                transactionImageService,
                adminAuditLogMapper,
                new AdminProperties(),
                CLOCK
        );
    }

    @Test
    void updateUserStatusRejectsDisablingCurrentAdmin() {
        ExpenseUser admin = new ExpenseUser();
        admin.setId(ADMIN_USER_ID);
        admin.setStatus("ACTIVE");
        when(userMapper.selectById(ADMIN_USER_ID)).thenReturn(admin);

        assertThatThrownBy(() -> service.updateUserStatus(
                ADMIN_USER_ID,
                ADMIN_USER_ID,
                new AdminUserStatusRequest("DISABLED", "误操作")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不能禁用当前管理员账号");
    }

    @Test
    void workbenchUsesFixedAttentionThresholds() {
        when(adminMapper.countUsers(null, null)).thenReturn(10L);
        when(adminMapper.countDisabledUsers()).thenReturn(2L);
        when(adminMapper.countUnverifiedEmailUsers()).thenReturn(3L);
        when(adminMapper.countFailedImportJobsSince(any())).thenReturn(4L);
        when(adminMapper.countLargeTransactionsSince(any(), eq(new BigDecimal("5000")))).thenReturn(5L);
        when(adminMapper.countHighFrequencyUserDaysSince(any(), eq(30))).thenReturn(6L);
        when(adminMapper.countActiveUsersSince(any())).thenReturn(7L);
        when(adminMapper.countAllTransactions()).thenReturn(8L);
        when(adminMapper.sumTransactions("EXPENSE")).thenReturn(new BigDecimal("123.45"));
        when(adminMapper.sumTransactions("INCOME")).thenReturn(new BigDecimal("88.00"));
        when(adminMapper.selectRecentRiskTransactions(any(), eq(new BigDecimal("5000")), eq(5))).thenReturn(List.of());
        when(adminMapper.selectRecentAuditLogs(eq(5))).thenReturn(List.of());

        AdminWorkbenchResponse response = service.workbench();

        verify(adminMapper).countLargeTransactionsSince(eq(LocalDate.now(CLOCK).minusDays(6).atStartOfDay()), eq(new BigDecimal("5000")));
        verify(adminMapper).countHighFrequencyUserDaysSince(eq(LocalDate.now(CLOCK).minusDays(6).atStartOfDay()), eq(30));
        assertThat(response.attentionItems())
                .extracting(AdminAttentionItemResponse::key)
                .contains("disabledUsers", "unverifiedEmailUsers", "failedImports7d", "largeTransactions7d", "highFrequencyUserDays7d");
    }

    @Test
    void getTransactionDetailRejectsMissingTransaction() {
        when(adminMapper.selectTransactionDetail(TRANSACTION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getTransactionDetail(TRANSACTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("记录不存在");
    }

    @Test
    void getTransactionDetailAttachesImagesAndRelatedAuditLogs() {
        AdminTransactionDetailResponse detail = new AdminTransactionDetailResponse();
        AdminTransactionResponse transaction = new AdminTransactionResponse();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(TARGET_USER_ID);
        transaction.setAmount(new BigDecimal("66.00"));
        transaction.setOccurredAt(LocalDateTime.of(2026, 6, 1, 12, 0));
        detail.setTransaction(transaction);
        when(adminMapper.selectTransactionDetail(TRANSACTION_ID)).thenReturn(detail);
        when(adminMapper.selectTransactionImagesForAdmin(TRANSACTION_ID)).thenReturn(List.of(
                new TransactionImageResponse(7L, "receipt.png", "image/png", 1024L, "/old-url", 1)
        ));
        when(adminMapper.selectRelatedAuditLogs("TRANSACTION", TRANSACTION_ID, 5)).thenReturn(List.of());

        AdminTransactionDetailResponse response = service.getTransactionDetail(TRANSACTION_ID);

        assertThat(response.getImages()).hasSize(1);
        assertThat(response.getImages().get(0).url()).isEqualTo("/api/v1/admin/transactions/88/images/7");
        verify(adminMapper).selectRelatedAuditLogs("TRANSACTION", TRANSACTION_ID, 5);
    }

    @Test
    void deleteTransactionSoftDeletesAndWritesAuditLog() {
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(TARGET_USER_ID);
        when(transactionMapper.selectOne(any())).thenReturn(transaction);

        service.deleteTransaction(ADMIN_USER_ID, TRANSACTION_ID, new AdminReasonRequest("异常记录"));

        verify(transactionService).deleteWithoutBusinessAudit(TARGET_USER_ID, TRANSACTION_ID);
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAdminUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(captor.getValue().getAction()).isEqualTo("TRANSACTION_DELETE");
        assertThat(captor.getValue().getTargetType()).isEqualTo("TRANSACTION");
        assertThat(captor.getValue().getTargetId()).isEqualTo(TRANSACTION_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("异常记录");
    }

    @Test
    void resetUserEmailClearsEmailRevokesTokensAndWritesAuditLog() {
        ExpenseUser user = new ExpenseUser();
        user.setId(TARGET_USER_ID);
        user.setEmail("demo@example.com");
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user);

        service.resetUserEmail(ADMIN_USER_ID, TARGET_USER_ID, new AdminReasonRequest("邮箱不可用"));

        verify(userMapper).update(isNull(), any());
        verify(authService).revokeTokens(TARGET_USER_ID);
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("RESET_USER_EMAIL");
        assertThat(captor.getValue().getReason()).isEqualTo("邮箱不可用");
    }
}
