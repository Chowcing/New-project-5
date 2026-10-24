package com.example.expense.recurring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.transaction.service.TransactionAiRecommendationService;
import com.example.expense.transaction.service.TransactionImageService;
import com.example.expense.transaction.service.TransactionRecommendationService;
import com.example.expense.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = RecurringRuleEditingIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:recurring_editing;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=MONTH",
                "spring.sql.init.schema-locations=classpath:nullable-update-schema.sql"
        })
@ActiveProfiles("test")
@Transactional
class RecurringRuleEditingIntegrationTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_ID = 20L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-23T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.recurring.mapper"
    })
    static class TestApplication {
    }

    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private RecurringRuleMapper ruleMapper;
    @Autowired
    private RecurringRuleRunMapper runMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private RecurringRuleService service;

    @BeforeEach
    void setUp() {
        CategoryService categories = mock(CategoryService.class);
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("餐饮");
        category.setType("EXPENSE");
        when(categories.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(category);

        PaymentMethodService payments = mock(PaymentMethodService.class);
        PaymentMethod payment = new PaymentMethod();
        payment.setId(PAYMENT_ID);
        payment.setUserId(USER_ID);
        payment.setName("微信");
        when(payments.requireOwned(USER_ID, PAYMENT_ID)).thenReturn(payment);

        BusinessAuditLogService audit = mock(BusinessAuditLogService.class);
        TransactionService transactions = new TransactionService(
                transactionMapper, categories, payments, mock(OnlinePlatformService.class),
                mock(TransactionImageService.class), mock(TransactionRecommendationService.class),
                mock(TransactionAiRecommendationService.class), mock(CacheInvalidationService.class), audit, CLOCK);
        service = new RecurringRuleService(
                ruleMapper, runMapper, categories, payments, transactions,
                new RecurringRunFailureRecorder(runMapper, CLOCK),
                new TransactionTemplate(transactionManager), CLOCK, audit);
    }

    @ParameterizedTest
    @CsvSource({
            "WEEKLY, true, 2026-10-30",
            "WEEKLY, false, 2026-10-30",
            "MONTHLY, true, 2026-11-23",
            "MONTHLY, false, 2026-11-23"
    })
    void editingAfterTodaysRunKeepsNextOccurrenceAndCreatesFutureRun(
            String scheduleType, boolean generate, LocalDate expectedNextDate) {
        RecurringRule rule = service.createRule(USER_ID, request(scheduleType, null, "原备注", "ACTIVE"));
        RecurringRuleRun original = onlyDueRun(TODAY);
        if (generate) {
            service.generateRun(USER_ID, original.getId());
        } else {
            service.skipRun(USER_ID, original.getId());
        }

        service.updateRule(USER_ID, rule.getId(), request(scheduleType, null, "修改后的备注", "ACTIVE"));

        assertNextDate(rule.getId(), expectedNextDate);
        RecurringRuleRun next = onlyDueRun(expectedNextDate);
        assertThat(next.getDueDate()).isEqualTo(expectedNextDate);
        assertThat(next.getNote()).isEqualTo("修改后的备注");
        assertThat(next.getId()).isNotEqualTo(original.getId());
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rule_runs WHERE id = ?", original.getId()))
                .containsEntry("status", generate ? "GENERATED" : "SKIPPED")
                .containsEntry("note", "原备注");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions", Long.class))
                .isEqualTo(generate ? 1L : 0L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void editingFinishedRuleDoesNotReactivateItsLastOccurrence(boolean generate) {
        RecurringRule rule = service.createRule(USER_ID, request("WEEKLY", TODAY, "原备注", "ACTIVE"));
        RecurringRuleRun original = onlyDueRun(TODAY);
        if (generate) {
            service.generateRun(USER_ID, original.getId());
        } else {
            service.skipRun(USER_ID, original.getId());
        }

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", TODAY, "修改后的备注", "ACTIVE"));

        assertNextDate(rule.getId(), null);
        assertThat(service.listDueRuns(USER_ID, TODAY.plusWeeks(1))).isEmpty();
        assertThat(service.listRuns(USER_ID, rule.getId())).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATED", "SKIPPED", "CANCELLED"})
    void changingScheduleSkipsTodaysProcessedDate(String status) {
        RecurringRule rule = service.createRule(USER_ID, request("MONTHLY", null, "原备注", "ACTIVE"));
        RecurringRuleRun run = onlyDueRun(TODAY);
        switch (status) {
            case "GENERATED" -> service.generateRun(USER_ID, run.getId());
            case "SKIPPED" -> service.skipRun(USER_ID, run.getId());
            case "CANCELLED" -> service.updateRule(USER_ID, rule.getId(),
                    request("MONTHLY", null, "原备注", "PAUSED"));
            default -> throw new AssertionError("未知状态 " + status);
        }

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", null, "新计划", "ACTIVE"));

        assertNextDate(rule.getId(), LocalDate.of(2026, 10, 30));
        assertThat(onlyDueRun(LocalDate.of(2026, 10, 30)).getDueDate())
                .isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void editingPreservesAnOverduePendingOccurrence() {
        RecurringRule rule = service.createRule(USER_ID, request("WEEKLY", null, "原备注", "ACTIVE"));
        jdbcTemplate.update("UPDATE recurring_rules SET next_run_date = ? WHERE id = ?",
                TODAY.minusWeeks(1), rule.getId());
        RecurringRuleRun overdue = onlyDueRun(TODAY);

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", null, "修改后的备注", "ACTIVE"));

        assertNextDate(rule.getId(), LocalDate.of(2026, 10, 16));
        RecurringRuleRun stored = onlyDueRun(TODAY);
        assertThat(stored.getId()).isEqualTo(overdue.getId());
        assertThat(stored.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(stored.getNote()).isEqualTo("修改后的备注");
    }

    @Test
    void resumingRuleDoesNotReuseCancelledOccurrence() {
        RecurringRule rule = service.createRule(USER_ID, request("WEEKLY", null, "原备注", "ACTIVE"));
        RecurringRuleRun original = onlyDueRun(TODAY);
        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", null, "原备注", "PAUSED"));

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", null, "恢复后", "ACTIVE"));

        assertNextDate(rule.getId(), LocalDate.of(2026, 10, 30));
        assertThat(onlyDueRun(LocalDate.of(2026, 10, 30)).getDueDate())
                .isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM recurring_rule_runs WHERE id = ?",
                String.class, original.getId())).isEqualTo("CANCELLED");
    }

    @Test
    void editingAlreadyStuckRuleSkipsConsecutiveProcessedDates() {
        RecurringRule rule = service.createRule(USER_ID, request("WEEKLY", null, "原备注", "ACTIVE"));
        service.skipRun(USER_ID, onlyDueRun(TODAY).getId());
        service.skipRun(USER_ID, onlyDueRun(TODAY.plusWeeks(1)).getId());
        // 模拟旧版本编辑后留下的、指向已处理日期的指针。
        jdbcTemplate.update("UPDATE recurring_rules SET next_run_date = ? WHERE id = ?", TODAY, rule.getId());

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", null, "修复后", "ACTIVE"));

        assertNextDate(rule.getId(), LocalDate.of(2026, 11, 6));
        assertThat(onlyDueRun(LocalDate.of(2026, 11, 6)).getDueDate())
                .isEqualTo(LocalDate.of(2026, 11, 6));
    }

    @Test
    void extendingFinishedRuleSkipsItsCompletedLastDate() {
        RecurringRule rule = service.createRule(USER_ID, request("WEEKLY", TODAY, "原备注", "ACTIVE"));
        service.skipRun(USER_ID, onlyDueRun(TODAY).getId());

        service.updateRule(USER_ID, rule.getId(), request("WEEKLY", TODAY.plusWeeks(2), "延期", "ACTIVE"));

        assertNextDate(rule.getId(), LocalDate.of(2026, 10, 30));
        assertThat(onlyDueRun(LocalDate.of(2026, 10, 30)).getDueDate())
                .isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @ParameterizedTest
    @CsvSource({
            "WEEKLY, 3, 2026-10-02, FRIDAY, , 2026-11-13",
            "WEEKLY, 1, 2026-11-01, FRIDAY, , 2026-11-06",
            "WEEKLY, 1, 2026-10-02, SATURDAY, , 2026-10-24",
            "MONTHLY, 1, 2026-10-02, , 24, 2026-10-24"
    })
    void changedScheduleUsesNewCadenceEvenWhenNextDateMovesEarlier(String scheduleType,
            int interval, LocalDate startDate, String weekday, Integer dayOfMonth, LocalDate expected) {
        RecurringRule rule = service.createRule(USER_ID, request(scheduleType, null, "原备注", "ACTIVE"));
        service.skipRun(USER_ID, onlyDueRun(TODAY).getId());

        service.updateRule(USER_ID, rule.getId(), scheduledRequest(
                scheduleType, interval, dayOfMonth, weekday, startDate, 0));

        assertNextDate(rule.getId(), expected);
        assertThat(onlyDueRun(expected).getDueDate()).isEqualTo(expected);
    }

    @Test
    void advancingAfterScheduleChangeSkipsLaterHistoricalOccurrence() {
        RecurringRule rule = service.createRule(USER_ID,
                scheduledRequest("WEEKLY", 2, null, "FRIDAY", TODAY, 30));
        service.skipRun(USER_ID, onlyDueRun(TODAY).getId());
        service.skipRun(USER_ID, onlyDueRun(TODAY).getId());
        assertNextDate(rule.getId(), LocalDate.of(2026, 11, 20));

        service.updateRule(USER_ID, rule.getId(),
                scheduledRequest("WEEKLY", 1, null, "FRIDAY", TODAY, 30));
        assertNextDate(rule.getId(), LocalDate.of(2026, 10, 30));
        RecurringRuleRun gap = onlyDueRun(TODAY);
        assertThat(gap.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        service.skipRun(USER_ID, gap.getId());

        assertNextDate(rule.getId(), LocalDate.of(2026, 11, 13));
        assertThat(onlyDueRun(TODAY).getDueDate()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    private void assertNextDate(Long ruleId, LocalDate expected) {
        assertThat(jdbcTemplate.queryForObject("SELECT next_run_date FROM recurring_rules WHERE id = ?",
                LocalDate.class, ruleId)).isEqualTo(expected);
    }

    private RecurringRuleRun onlyDueRun(LocalDate date) {
        var runs = service.listDueRuns(USER_ID, date);
        assertThat(runs).hasSize(1);
        return runs.get(0);
    }

    private RecurringRuleRequest request(String scheduleType, LocalDate endDate, String note, String status) {
        return new RecurringRuleRequest("午餐规则", "EXPENSE", "午餐", new BigDecimal("25.00"),
                "OFFLINE", null, "食堂", PAYMENT_ID, CATEGORY_ID, note,
                scheduleType, 1, "MONTHLY".equals(scheduleType) ? 23 : null,
                "WEEKLY".equals(scheduleType) ? "FRIDAY" : null,
                LocalDate.of(2026, 10, 2), endDate, 0, status);
    }

    private RecurringRuleRequest scheduledRequest(String scheduleType, int interval, Integer dayOfMonth,
            String weekday, LocalDate startDate, int reminderDaysBefore) {
        return new RecurringRuleRequest("午餐规则", "EXPENSE", "午餐", new BigDecimal("25.00"),
                "OFFLINE", null, "食堂", PAYMENT_ID, CATEGORY_ID, "原备注",
                scheduleType, interval, dayOfMonth, weekday, startDate, null, reminderDaysBefore, "ACTIVE");
    }
}
