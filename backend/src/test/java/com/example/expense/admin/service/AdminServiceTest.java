package com.example.expense.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.dto.AdminUserStatusRequest;
import com.example.expense.admin.entity.AdminAuditLog;
import com.example.expense.admin.mapper.AdminAuditLogMapper;
import com.example.expense.admin.mapper.AdminMapper;
import com.example.expense.auth.mapper.RefreshTokenMapper;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.transaction.service.TransactionService;
import com.example.expense.user.entity.ExpenseUser;
import com.example.expense.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
    private RefreshTokenMapper refreshTokenMapper;
    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private TransactionService transactionService;
    @Mock
    private AdminAuditLogMapper adminAuditLogMapper;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(
                adminMapper,
                userMapper,
                refreshTokenMapper,
                transactionMapper,
                transactionService,
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
    void deleteTransactionSoftDeletesAndWritesAuditLog() {
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(TARGET_USER_ID);
        when(transactionMapper.selectOne(any())).thenReturn(transaction);

        service.deleteTransaction(ADMIN_USER_ID, TRANSACTION_ID, new AdminReasonRequest("异常记录"));

        verify(transactionService).delete(TARGET_USER_ID, TRANSACTION_ID);
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAdminUserId()).isEqualTo(ADMIN_USER_ID);
        assertThat(captor.getValue().getAction()).isEqualTo("TRANSACTION_DELETE");
        assertThat(captor.getValue().getTargetType()).isEqualTo("TRANSACTION");
        assertThat(captor.getValue().getTargetId()).isEqualTo(TRANSACTION_ID);
        assertThat(captor.getValue().getReason()).isEqualTo("异常记录");
    }
}
