package com.example.expense.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.expense.budget.dto.BudgetRequest;
import com.example.expense.budget.entity.Budget;
import com.example.expense.budget.mapper.BudgetMapper;
import com.example.expense.budget.service.BudgetService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.recurring.mapper.RecurringRuleRunMapper;
import com.example.expense.recurring.service.RecurringRuleService;
import com.example.expense.recurring.service.RecurringRunFailureRecorder;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.entity.ExpenseTransaction;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
        classes = NullableFieldUpdateIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:nullable_updates;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=MONTH",
                "spring.sql.init.schema-locations=classpath:nullable-update-schema.sql"
        })
@ActiveProfiles("test")
@Transactional
class NullableFieldUpdateIntegrationTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_ID = 20L;
    private static final Long PLATFORM_ID = 30L;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-23T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final BigDecimal AMOUNT = new BigDecimal("25.00");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.budget.mapper",
            "com.example.expense.recurring.mapper"
    })
    static class TestApplication {
    }

    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private BudgetMapper budgetMapper;
    @Autowired
    private RecurringRuleMapper ruleMapper;
    @Autowired
    private RecurringRuleRunMapper runMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionService transactionService;
    private BudgetService budgetService;
    private RecurringRuleService recurringService;

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

        OnlinePlatformService platforms = mock(OnlinePlatformService.class);
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(PLATFORM_ID);
        platform.setUserId(USER_ID);
        platform.setName("美团");
        when(platforms.requireOwned(USER_ID, PLATFORM_ID)).thenReturn(platform);

        CacheInvalidationService cache = mock(CacheInvalidationService.class);
        BusinessAuditLogService audit = mock(BusinessAuditLogService.class);
        transactionService = new TransactionService(
                transactionMapper, categories, payments, platforms,
                mock(TransactionImageService.class),
                mock(TransactionRecommendationService.class),
                mock(TransactionAiRecommendationService.class), cache, audit, CLOCK);
        budgetService = new BudgetService(budgetMapper, categories, cache, audit);
        recurringService = new RecurringRuleService(
                ruleMapper, runMapper, categories, payments, transactionService,
                new RecurringRunFailureRecorder(runMapper, CLOCK),
                new TransactionTemplate(transactionManager), CLOCK, audit);
    }

    @Test
    void editingTransactionClearsTextAndOnlineFieldsInDatabase() {
        ExpenseTransaction created = transactionService.create(USER_ID,
                transactionRequest("午餐", "ONLINE", "美团", PLATFORM_ID, null, "少辣"));

        transactionService.update(USER_ID, created.getId(),
                transactionRequest("  ", "OFFLINE", null, null, "食堂", "  "));

        var stored = jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE id = ?", created.getId());
        assertThat(stored).containsEntry("item_name", null)
                .containsEntry("note", null)
                .containsEntry("online_app", null)
                .containsEntry("online_platform_id", null)
                .containsEntry("channel", "OFFLINE")
                .containsEntry("offline_place", "食堂")
                .containsEntry("user_id", USER_ID)
                .containsEntry("payment_method_id", PAYMENT_ID)
                .containsEntry("payment_method_name", "微信")
                .containsEntry("category_id", CATEGORY_ID)
                .containsEntry("amount", AMOUNT);
    }

    @Test
    void switchingTransactionOnlineClearsOfflinePlaceInDatabase() {
        ExpenseTransaction created = transactionService.create(USER_ID,
                transactionRequest("午餐", "OFFLINE", null, null, "食堂", "保留备注"));

        transactionService.update(USER_ID, created.getId(),
                transactionRequest("午餐", "ONLINE", "美团", PLATFORM_ID, null, "保留备注"));

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM transactions WHERE id = ?", created.getId()))
                .containsEntry("offline_place", null)
                .containsEntry("online_app", "美团")
                .containsEntry("online_platform_id", PLATFORM_ID)
                .containsEntry("note", "保留备注");
    }

    @Test
    void changingCategoryBudgetToMonthlyBudgetClearsCategoryInDatabase() {
        Budget created = budgetService.create(USER_ID, new BudgetRequest("2026-10", CATEGORY_ID, AMOUNT));

        budgetService.update(USER_ID, created.getId(), new BudgetRequest("2026-10", null, AMOUNT));

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM budgets WHERE id = ?", created.getId()))
                .containsEntry("category_id", null)
                .containsEntry("user_id", USER_ID)
                .containsEntry("month", "2026-10")
                .containsEntry("amount", AMOUNT);
        assertThat(budgetService.list(USER_ID, "2026-10")).singleElement()
                .extracting(Budget::getCategoryId).isNull();
    }

    @Test
    void editingRuleClearsOptionalFieldsAndPendingSnapshotInDatabase() {
        RecurringRule created = recurringService.createRule(USER_ID,
                ruleRequest("午餐", "ONLINE", "美团", null, "少辣",
                        "WEEKLY", null, "FRIDAY", TODAY.plusMonths(2)));
        RecurringRuleRun run = recurringService.listDueRuns(USER_ID, TODAY).get(0);
        jdbcTemplate.update("UPDATE recurring_rule_runs SET status = 'FAILED', error_message = ? WHERE id = ?",
                "旧错误", run.getId());

        recurringService.updateRule(USER_ID, created.getId(),
                ruleRequest("  ", "OFFLINE", null, "食堂", "  ",
                        "MONTHLY", 23, null, null));

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rules WHERE id = ?", created.getId()))
                .containsEntry("item_name", null)
                .containsEntry("online_app", null)
                .containsEntry("note", null)
                .containsEntry("weekday", null)
                .containsEntry("end_date", null)
                .containsEntry("day_of_month", 23)
                .containsEntry("offline_place", "食堂");
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rule_runs WHERE id = ?", run.getId()))
                .containsEntry("item_name", null)
                .containsEntry("online_app", null)
                .containsEntry("note", null)
                .containsEntry("error_message", null)
                .containsEntry("offline_place", "食堂")
                .containsEntry("payment_method_id", PAYMENT_ID)
                .containsEntry("category_id", CATEGORY_ID);
    }

    @Test
    void switchingRuleToWeeklyOnlineClearsMonthlyDayAndOfflineSnapshotInDatabase() {
        RecurringRule created = recurringService.createRule(USER_ID,
                ruleRequest("午餐", "OFFLINE", null, "食堂", "保留备注",
                        "MONTHLY", 23, null, null));
        RecurringRuleRun run = recurringService.listDueRuns(USER_ID, TODAY).get(0);

        recurringService.updateRule(USER_ID, created.getId(),
                ruleRequest("午餐", "ONLINE", "美团", null, "保留备注",
                        "WEEKLY", null, "FRIDAY", null));

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rules WHERE id = ?", created.getId()))
                .containsEntry("day_of_month", null)
                .containsEntry("offline_place", null)
                .containsEntry("weekday", "FRIDAY")
                .containsEntry("online_app", "美团");
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rule_runs WHERE id = ?", run.getId()))
                .containsEntry("offline_place", null)
                .containsEntry("online_app", "美团")
                .containsEntry("note", "保留备注");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void finishingLastOccurrenceClearsNextRunDateInDatabase(boolean generate) {
        RecurringRule created = recurringService.createRule(USER_ID,
                ruleRequest("午餐", "OFFLINE", null, "食堂", null,
                        "WEEKLY", null, "FRIDAY", TODAY));
        RecurringRuleRun run = recurringService.listDueRuns(USER_ID, TODAY).get(0);

        if (generate) {
            recurringService.generateRun(USER_ID, run.getId());
        } else {
            recurringService.skipRun(USER_ID, run.getId());
        }

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM recurring_rules WHERE id = ?", created.getId()))
                .containsEntry("next_run_date", null)
                .containsEntry("user_id", USER_ID);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM recurring_rule_runs WHERE id = ?",
                String.class, run.getId())).isEqualTo(generate ? "GENERATED" : "SKIPPED");
        assertThat(recurringService.listDueRuns(USER_ID, TODAY.plusWeeks(1))).isEmpty();
    }

    private TransactionRequest transactionRequest(String itemName, String channel, String onlineApp,
            Long platformId, String offlinePlace, String note) {
        return new TransactionRequest("EXPENSE", itemName, AMOUNT,
                LocalDateTime.of(2026, 10, 23, 12, 0), channel, onlineApp, platformId,
                offlinePlace, PAYMENT_ID, CATEGORY_ID, note);
    }

    private RecurringRuleRequest ruleRequest(String itemName, String channel, String onlineApp,
            String offlinePlace, String note, String scheduleType, Integer dayOfMonth,
            String weekday, LocalDate endDate) {
        return new RecurringRuleRequest("午餐规则", "EXPENSE", itemName, AMOUNT,
                channel, onlineApp, offlinePlace, PAYMENT_ID, CATEGORY_ID, note,
                scheduleType, 1, dayOfMonth, weekday, TODAY, endDate, 0, "ACTIVE");
    }
}
