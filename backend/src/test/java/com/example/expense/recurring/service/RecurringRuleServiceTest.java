package com.example.expense.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
                failureRecorder
        );
    }

    @Test
    void generateRunRetriesFailedRunAndUsesDueDateAsTransactionTime() {
        RecurringRuleRun run = run("FAILED", LocalDate.of(2026, 5, 15));
        RecurringRule rule = rule();
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(9001L);
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
        when(recurringRuleRunMapper.selectOne(anyRunWrapper())).thenReturn(run);
        when(recurringRuleMapper.selectOne(anyRuleWrapper())).thenReturn(rule);
        when(transactionService.create(eq(USER_ID), any(TransactionRequest.class)))
                .thenThrow(new IllegalArgumentException("分类不存在"));

        assertThatThrownBy(() -> service.generateRun(USER_ID, RUN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分类不存在");

        verify(failureRecorder).recordFailure(run, "分类不存在");
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

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<RecurringRuleRun> anyRunWrapper() {
        return any(LambdaQueryWrapper.class);
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<RecurringRule> anyRuleWrapper() {
        return any(LambdaQueryWrapper.class);
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
