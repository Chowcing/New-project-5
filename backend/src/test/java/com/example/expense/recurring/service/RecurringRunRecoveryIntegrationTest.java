package com.example.expense.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.recurring.scheduler.RecurringRuleScheduler;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.transaction.service.TransactionAiRecommendationService;
import com.example.expense.transaction.service.TransactionImageService;
import com.example.expense.transaction.service.TransactionRecommendationService;
import com.example.expense.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = RecurringRunRecoveryIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:recurring_recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=MONTH",
                "spring.sql.init.schema-locations=classpath:nullable-update-schema.sql,classpath:db/migration/V14__index_recurring_processing_recovery.sql"
        })
@ActiveProfiles("test")
class RecurringRunRecoveryIntegrationTest {
    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 2002L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_ID = 20L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({"com.example.expense.transaction.mapper", "com.example.expense.recurring.mapper"})
    static class TestApplication {
        @Bean
        BusinessAuditLogService audit() {
            return mock(BusinessAuditLogService.class);
        }

        @Bean
        RecurringRunFailureRecorder failureRecorder(RecurringRuleRunMapper mapper) {
            return new RecurringRunFailureRecorder(mapper, CLOCK);
        }

        @Bean
        RecurringRuleService recurringService(RecurringRuleMapper rules, RecurringRuleRunMapper runs,
                TransactionMapper transactions, RecurringRunFailureRecorder failures,
                BusinessAuditLogService audit, PlatformTransactionManager transactionManager) {
            CategoryService categories = mock(CategoryService.class);
            Category category = new Category();
            category.setId(CATEGORY_ID);
            category.setName("餐饮");
            category.setType("EXPENSE");
            when(categories.requireOwned(anyLong(), eq(CATEGORY_ID))).thenReturn(category);
            PaymentMethodService payments = mock(PaymentMethodService.class);
            PaymentMethod payment = new PaymentMethod();
            payment.setId(PAYMENT_ID);
            payment.setName("微信");
            when(payments.requireOwned(anyLong(), eq(PAYMENT_ID))).thenReturn(payment);
            TransactionService transactionService = new TransactionService(
                    transactions, categories, payments, mock(OnlinePlatformService.class),
                    mock(TransactionImageService.class), mock(TransactionRecommendationService.class),
                    mock(TransactionAiRecommendationService.class), mock(CacheInvalidationService.class), audit, CLOCK);
            return new RecurringRuleService(rules, runs, categories, payments, transactionService,
                    failures, new TransactionTemplate(transactionManager), CLOCK, audit);
        }
    }

    @Autowired
    private RecurringRuleService service;
    @Autowired
    private RecurringRunFailureRecorder failureRecorder;
    @Autowired
    private RecurringRuleRunMapper runMapper;
    @Autowired
    private BusinessAuditLogService audit;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int ruleSequence;

    @BeforeEach
    void setUp() {
        reset(audit);
        jdbcTemplate.update("DELETE FROM recurring_rule_runs");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM recurring_rules");
        ruleSequence = 0;
    }

    @Test
    void interruptionRollsBackClaimTransactionAndRuleAdvanceThenAllowsRetry() {
        RecurringRuleRun run = createRun(USER_ID);
        Error interruption = new AssertionError("模拟进程中断，不执行业务失败处理");
        doThrow(interruption).when(audit).recordSuccess(eq(USER_ID), eq("RECURRING_RUN_GENERATE"),
                eq("RECURRING_RUN"), eq(run.getId()), eq("RECURRING"));

        assertThatThrownBy(() -> service.generateRun(USER_ID, run.getId())).isSameAs(interruption);

        assertThat(storedStatus(run)).isEqualTo("PENDING");
        assertNoTransactionAndNoAdvance(run);
        reset(audit);
        RecurringRuleRun generated = service.generateRun(USER_ID, run.getId());
        assertThat(generated.getStatus()).isEqualTo("GENERATED");
        assertThat(storedStatus(run)).isEqualTo("GENERATED");
        assertThat(transactionCount()).isEqualTo(1L);
    }

    @Test
    void businessFailureAfterWritesLeavesRetryableRunWithoutRolledBackTransactionId() {
        RecurringRuleRun run = createRun(USER_ID);
        doThrow(new IllegalArgumentException("模拟生成失败")).when(audit)
                .recordSuccess(eq(USER_ID), eq("RECURRING_RUN_GENERATE"), eq("RECURRING_RUN"),
                        eq(run.getId()), eq("RECURRING"));

        assertThatThrownBy(() -> service.generateRun(USER_ID, run.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("模拟生成失败");

        assertThat(storedStatus(run)).isEqualTo("FAILED");
        assertNoTransactionAndNoAdvance(run);
        reset(audit);
        service.generateRun(USER_ID, run.getId());
        assertThat(storedStatus(run)).isEqualTo("GENERATED");
        assertThat(transactionCount()).isEqualTo(1L);
    }

    @Test
    void concurrentGenerationHoldsClaimUntilCommitAndCreatesOnlyOneTransaction() throws Exception {
        RecurringRuleRun run = createRun(USER_ID);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            inserted.countDown();
            await(release);
            return null;
        }).when(audit).recordSuccess(eq(USER_ID), eq("TRANSACTION_CREATE"), eq("TRANSACTION"), anyLong(), eq("USER"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.generateRun(USER_ID, run.getId()));
            assertThat(inserted.await(3, TimeUnit.SECONDS)).isTrue();
            // 另一个连接应仍看到提交前的待处理状态，而非已提交的 PROCESSING。
            assertThat(storedStatus(run)).isEqualTo("PENDING");
            var second = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    service.generateRun(USER_ID, run.getId());
                    return "重复生成";
                } catch (IllegalArgumentException ex) {
                    return ex.getMessage();
                }
            });
            assertThat(secondStarted.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(first.get(3, TimeUnit.SECONDS).getStatus()).isEqualTo("GENERATED");
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("该周期记录已处理");
            assertThat(transactionCount()).isEqualTo(1L);
            assertThat(storedStatus(run)).isEqualTo("GENERATED");
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATED", "SKIPPED", "CANCELLED", "PROCESSING"})
    void lateFailureRecordingDoesNotOverwriteAnotherAttemptsState(String status) {
        RecurringRuleRun stale = createRun(USER_ID);
        if ("GENERATED".equals(status)) {
            service.generateRun(USER_ID, stale.getId());
        } else {
            jdbcTemplate.update("UPDATE recurring_rule_runs SET status = ?, processed_at = ? WHERE id = ?",
                    status, NOW, stale.getId());
        }

        failureRecorder.recordFailure(stale, "迟到的错误");

        assertThat(storedStatus(stale)).isEqualTo(status);
        assertThat(transactionCount()).isEqualTo("GENERATED".equals(status) ? 1L : 0L);
    }

    @Test
    void dueQueryRecoversOnlyCurrentUsersOldUnlinkedProcessingRuns() {
        RecurringRuleRun old = processingRun(USER_ID, NOW.minusMinutes(6), null);
        RecurringRuleRun recent = processingRun(USER_ID, NOW.minusMinutes(4), null);
        RecurringRuleRun linked = processingRun(USER_ID, NOW.minusMinutes(6), 99L);
        RecurringRuleRun other = processingRun(OTHER_USER_ID, NOW.minusMinutes(6), null);

        var due = service.listDueRuns(USER_ID, TODAY);

        assertThat(due).extracting(RecurringRuleRun::getId).containsExactly(old.getId());
        assertThat(storedStatus(old)).isEqualTo("FAILED");
        assertThat(storedStatus(recent)).isEqualTo("PROCESSING");
        assertThat(storedStatus(linked)).isEqualTo("PROCESSING");
        assertThat(storedStatus(other)).isEqualTo("PROCESSING");
        assertThat(transactionCount()).isZero();
        service.generateRun(USER_ID, old.getId());
        assertThat(transactionCount()).isEqualTo(1L);
    }

    @Test
    void historyQueryRecoversLegacyProcessingWithMissingTimestamp() {
        RecurringRuleRun old = processingRun(USER_ID, null, null);

        assertThat(service.listRuns(USER_ID, old.getRuleId())).singleElement()
                .extracting(RecurringRuleRun::getStatus).isEqualTo("FAILED");
        service.skipRun(USER_ID, old.getId());
        assertThat(storedStatus(old)).isEqualTo("SKIPPED");
        assertThat(transactionCount()).isZero();
    }

    @Test
    void dailySchedulerRecoversOldRunsForAllUsersAndRecoveryIsIdempotent() {
        RecurringRuleRun first = processingRun(USER_ID, NOW.minusMinutes(6), null);
        RecurringRuleRun second = processingRun(OTHER_USER_ID, NOW.minusMinutes(6), null);
        RecurringRuleScheduler scheduler = new RecurringRuleScheduler(service, CLOCK);

        scheduler.seedDueRuns();
        scheduler.seedDueRuns();

        assertThat(storedStatus(first)).isEqualTo("FAILED");
        assertThat(storedStatus(second)).isEqualTo("FAILED");
        assertThat(transactionCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recurring_rule_runs", Long.class)).isEqualTo(2L);
    }

    private RecurringRuleRun processingRun(Long userId, LocalDateTime claimedAt, Long transactionId) {
        RecurringRuleRun run = createRun(userId);
        jdbcTemplate.update("UPDATE recurring_rule_runs SET status = 'PROCESSING', processed_at = ?, transaction_id = ? WHERE id = ?",
                claimedAt, transactionId, run.getId());
        return run;
    }

    private RecurringRuleRun createRun(Long userId) {
        var rule = service.createRule(userId, new RecurringRuleRequest(
                "中断恢复测试" + ++ruleSequence, "EXPENSE", "午餐", new BigDecimal("25.00"),
                "OFFLINE", null, "食堂", PAYMENT_ID, CATEGORY_ID, null,
                "WEEKLY", 1, null, "SATURDAY", TODAY, null, 0, "ACTIVE"));
        service.seedDueRunsForUser(userId, TODAY);
        return runMapper.selectOne(new LambdaQueryWrapper<RecurringRuleRun>()
                .eq(RecurringRuleRun::getUserId, userId).eq(RecurringRuleRun::getRuleId, rule.getId()));
    }

    private String storedStatus(RecurringRuleRun run) {
        return jdbcTemplate.queryForObject("SELECT status FROM recurring_rule_runs WHERE id = ?", String.class, run.getId());
    }

    private long transactionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Long.class);
    }

    private void assertNoTransactionAndNoAdvance(RecurringRuleRun run) {
        assertThat(transactionCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT transaction_id FROM recurring_rule_runs WHERE id = ?",
                Long.class, run.getId())).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT next_run_date FROM recurring_rules WHERE id = ?",
                LocalDate.class, run.getRuleId())).isEqualTo(TODAY);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待生成操作被中断", ex);
        }
    }
}
