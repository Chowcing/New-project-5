package com.example.expense.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringRuleServiceTest {
    private static final Long USER_ID = 1001L;
    private static final Long RULE_ID = 77L;
    private static final Long RUN_ID = 88L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private RecurringRuleMapper recurringRuleMapper;
    @Mock
    private RecurringRuleRunMapper recurringRuleRunMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private RecurringRunFailureRecorder failureRecorder;

    private RecurringRuleService service;

    @BeforeEach
    void setUp() {
        service = new RecurringRuleService(
                recurringRuleMapper,
                recurringRuleRunMapper,
                categoryService,
                paymentMethodService,
                transactionService,
                failureRecorder,
                CLOCK
        );
    }

    @Test
    void createRuleUsesConfiguredClockWhenCalculatingNextRunDate() {
        Category category = new Category();
        category.setId(2001L);
        category.setName("居住");
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(3001L);
        paymentMethod.setName("银行卡");
        when(categoryService.requireOwned(USER_ID, 2001L)).thenReturn(category);
        when(paymentMethodService.requireOwned(USER_ID, 3001L)).thenReturn(paymentMethod);

        RecurringRule created = service.createRule(USER_ID, new RecurringRuleRequest(
                "房租",
                "EXPENSE",
                "房租",
                new BigDecimal("2500.00"),
                "OFFLINE",
                null,
                "小区",
                3001L,
                2001L,
                null,
                "MONTHLY",
                1,
                15,
                null,
                LocalDate.of(2026, 5, 1),
                null,
                0,
                "ACTIVE"
        ));

        assertThat(created.getNextRunDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        verify(recurringRuleMapper).insert(created);
    }

    @Test
    void generateRunRetriesFailedRunAndUsesDueDateAsTransactionTime() {
        RecurringRuleRun run = run("FAILED", LocalDate.of(2026, 5, 15));
        RecurringRule rule = rule();
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(9001L);
        when(recurringRuleRunMapper.update(eq(null), anyRunUpdateWrapper())).thenReturn(1);
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(run);
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class))).thenReturn(transaction);

        RecurringRuleRun generated = service.generateRun(USER_ID, RUN_ID);

        ArgumentCaptor<TransactionRequest> requestCaptor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(eq(USER_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().occurredAt()).isEqualTo(LocalDate.of(2026, 5, 15).atStartOfDay());
        assertThat(generated.getStatus()).isEqualTo("GENERATED");
        assertThat(generated.getTransactionId()).isEqualTo(9001L);
        assertThat(rule.getNextRunDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        verify(recurringRuleRunMapper).updateById(run);
        verify(recurringRuleMapper).updateById(rule);
    }

    @Test
    void generateRunRecordsFailureInSeparateRecorderWhenTransactionCreationFails() {
        RecurringRuleRun run = run("PENDING", LocalDate.of(2026, 5, 15));
        RecurringRule rule = rule();
        when(recurringRuleRunMapper.update(eq(null), anyRunUpdateWrapper())).thenReturn(1);
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(run);
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class)))
                .thenThrow(new IllegalArgumentException("分类不存在"));

        assertThatThrownBy(() -> service.generateRun(USER_ID, RUN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分类不存在");

        verify(failureRecorder).recordFailure(run, "分类不存在");
    }

    @Test
    void generateRunDoesNotCreateTransactionWhenAnotherRequestAlreadyClaimedRun() {
        RecurringRuleRun run = run("GENERATED", LocalDate.of(2026, 5, 15));
        when(recurringRuleRunMapper.update(eq(null), anyRunUpdateWrapper())).thenReturn(0);
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(run);

        assertThatThrownBy(() -> service.generateRun(USER_ID, RUN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("该周期记录已处理");

        verify(transactionService, never()).create(eq(USER_ID), any(TransactionRequest.class));
    }

    @Test
    void updateRuleRefreshesFailedRunSnapshotSoRetryUsesLatestRuleData() {
        RecurringRule rule = rule();
        RecurringRuleRun failedRun = run("FAILED", LocalDate.of(2026, 5, 15));
        failedRun.setErrorMessage("分类不存在");
        Category category = category(2002L, "新分类");
        PaymentMethod paymentMethod = paymentMethod(3002L, "新卡");
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(failedRun);
        when(categoryService.requireOwned(USER_ID, 2002L)).thenReturn(category);
        when(paymentMethodService.requireOwned(USER_ID, 3002L)).thenReturn(paymentMethod);

        service.updateRule(USER_ID, RULE_ID, request("新房租", new BigDecimal("2600.00"), 3002L, 2002L, 10, "ACTIVE"));

        assertThat(failedRun.getStatus()).isEqualTo("FAILED");
        assertThat(failedRun.getRuleName()).isEqualTo("新房租");
        assertThat(failedRun.getAmount()).isEqualByComparingTo("2600.00");
        assertThat(failedRun.getPaymentMethodId()).isEqualTo(3002L);
        assertThat(failedRun.getCategoryId()).isEqualTo(2002L);
        assertThat(failedRun.getDueDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(failedRun.getErrorMessage()).isNull();
        verify(recurringRuleRunMapper).updateById(failedRun);
    }

    @Test
    void pauseRuleCancelsFailedRunsThatAreStillActionable() {
        RecurringRule rule = rule();
        RecurringRuleRun failedRun = run("FAILED", LocalDate.of(2026, 5, 15));
        Category category = category(2001L, "居住");
        PaymentMethod paymentMethod = paymentMethod(3001L, "银行卡");
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(failedRun);
        when(recurringRuleRunMapper.selectList(anyRunWrapper())).thenReturn(List.of(failedRun));
        when(categoryService.requireOwned(USER_ID, 2001L)).thenReturn(category);
        when(paymentMethodService.requireOwned(USER_ID, 3001L)).thenReturn(paymentMethod);

        service.updateRule(USER_ID, RULE_ID, request("房租", new BigDecimal("2500.00"), 3001L, 2001L, 15, "PAUSED"));

        assertThat(failedRun.getStatus()).isEqualTo("CANCELLED");
        assertThat(failedRun.getErrorMessage()).isEqualTo("规则已暂停");
        assertThat(failedRun.getProcessedAt()).isNotNull();
        verify(recurringRuleRunMapper).updateById(failedRun);
    }

    @Test
    void deleteRuleCancelsFailedRunsThatAreStillActionable() {
        RecurringRule rule = rule();
        RecurringRuleRun failedRun = run("FAILED", LocalDate.of(2026, 5, 15));
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(recurringRuleRunMapper.selectList(anyRunWrapper())).thenReturn(List.of(failedRun));

        service.deleteRule(USER_ID, RULE_ID);

        assertThat(failedRun.getStatus()).isEqualTo("CANCELLED");
        assertThat(failedRun.getErrorMessage()).isEqualTo("规则已删除");
        assertThat(failedRun.getProcessedAt()).isNotNull();
        verify(recurringRuleMapper).deleteById(RULE_ID);
        verify(recurringRuleRunMapper).updateById(failedRun);
    }

    private RecurringRuleRun run(String status, LocalDate dueDate) {
        RecurringRuleRun run = new RecurringRuleRun();
        run.setId(RUN_ID);
        run.setUserId(USER_ID);
        run.setRuleId(RULE_ID);
        run.setRuleName("房租");
        run.setDueDate(dueDate);
        run.setReminderDaysBefore(0);
        run.setType("EXPENSE");
        run.setItemName("房租");
        run.setAmount(new BigDecimal("2500.00"));
        run.setChannel("OFFLINE");
        run.setOfflinePlace("小区");
        run.setPaymentMethodId(3001L);
        run.setCategoryId(2001L);
        run.setStatus(status);
        return run;
    }

    private RecurringRuleRequest request(
            String name,
            BigDecimal amount,
            Long paymentMethodId,
            Long categoryId,
            Integer dayOfMonth,
            String status
    ) {
        return new RecurringRuleRequest(
                name,
                "EXPENSE",
                name,
                amount,
                "OFFLINE",
                null,
                "小区",
                paymentMethodId,
                categoryId,
                null,
                "MONTHLY",
                1,
                dayOfMonth,
                null,
                LocalDate.of(2026, 1, dayOfMonth),
                null,
                0,
                status
        );
    }

    private Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private PaymentMethod paymentMethod(Long id, String name) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(id);
        paymentMethod.setName(name);
        return paymentMethod;
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<RecurringRuleRun> anyRunWrapper() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<RecurringRule> anyRuleWrapper() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<RecurringRuleRun> anyRunUpdateWrapper() {
        return any(UpdateWrapper.class);
    }

    private RecurringRule rule() {
        RecurringRule rule = new RecurringRule();
        rule.setId(RULE_ID);
        rule.setUserId(USER_ID);
        rule.setName("房租");
        rule.setType("EXPENSE");
        rule.setItemName("房租");
        rule.setAmount(new BigDecimal("2500.00"));
        rule.setChannel("OFFLINE");
        rule.setOfflinePlace("小区");
        rule.setPaymentMethodId(3001L);
        rule.setCategoryId(2001L);
        rule.setScheduleType("MONTHLY");
        rule.setIntervalValue(1);
        rule.setDayOfMonth(15);
        rule.setStartDate(LocalDate.of(2026, 1, 15));
        rule.setNextRunDate(LocalDate.of(2026, 5, 15));
        rule.setStatus("ACTIVE");
        rule.setDeleted(0);
        return rule;
    }
}
