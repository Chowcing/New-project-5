package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.expense.businessaudit.mapper.BusinessAuditLogMapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.dto.CategoryRequest;
import com.example.expense.category.mapper.CategoryMapper;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.config.StorageProperties;
import com.example.expense.payment.mapper.PaymentMethodMapper;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.mapper.OnlinePlatformMapper;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.recurring.mapper.RecurringRuleMapper;
import com.example.expense.transaction.mapper.TransactionImageMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = TransactionRestoreReferenceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransactionRestoreReferenceIntegrationTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_METHOD_ID = 30L;
    private static final Long PLATFORM_ID = 40L;
    private static final Long TRANSACTION_ID = 201L;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.businessaudit.mapper",
            "com.example.expense.category.mapper",
            "com.example.expense.payment.mapper",
            "com.example.expense.platform.mapper"
    })
    static class TestApplication {
        @Bean
        Clock appClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-30T00:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        StorageProperties storageProperties() {
            return new StorageProperties();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        CacheInvalidationService cacheInvalidationService(
                StringRedisTemplate redisTemplate
        ) {
            return new CacheInvalidationService(redisTemplate);
        }

        @Bean
        BusinessAuditLogService businessAuditLogService(
                BusinessAuditLogMapper mapper
        ) {
            return new BusinessAuditLogService(mapper);
        }

        @Bean
        RecurringRuleMapper recurringRuleMapper() {
            return mock(RecurringRuleMapper.class);
        }

        @Bean
        CategoryService categoryService(
                CategoryMapper categoryMapper,
                TransactionMapper transactionMapper,
                RecurringRuleMapper recurringRuleMapper,
                CacheInvalidationService cacheInvalidationService,
                BusinessAuditLogService businessAuditLogService
        ) {
            return new CategoryService(
                    categoryMapper,
                    transactionMapper,
                    recurringRuleMapper,
                    cacheInvalidationService,
                    businessAuditLogService);
        }

        @Bean
        PaymentMethodService paymentMethodService(
                PaymentMethodMapper paymentMethodMapper,
                TransactionMapper transactionMapper,
                RecurringRuleMapper recurringRuleMapper,
                CacheInvalidationService cacheInvalidationService,
                BusinessAuditLogService businessAuditLogService
        ) {
            return new PaymentMethodService(
                    paymentMethodMapper,
                    transactionMapper,
                    recurringRuleMapper,
                    cacheInvalidationService,
                    businessAuditLogService);
        }

        @Bean
        OnlinePlatformService onlinePlatformService(
                OnlinePlatformMapper onlinePlatformMapper,
                TransactionMapper transactionMapper,
                CacheInvalidationService cacheInvalidationService,
                BusinessAuditLogService businessAuditLogService
        ) {
            return new OnlinePlatformService(
                    onlinePlatformMapper,
                    transactionMapper,
                    cacheInvalidationService,
                    businessAuditLogService);
        }

        @Bean
        TransactionImageService transactionImageService(
                TransactionImageMapper imageMapper,
                TransactionMapper transactionMapper,
                StorageProperties storageProperties,
                Clock clock
        ) {
            return new TransactionImageService(
                    imageMapper, transactionMapper, storageProperties, clock);
        }

        @Bean
        TransactionService transactionService(
                TransactionMapper transactionMapper,
                CategoryService categoryService,
                PaymentMethodService paymentMethodService,
                OnlinePlatformService onlinePlatformService,
                TransactionImageService imageService,
                CacheInvalidationService cacheService,
                BusinessAuditLogService auditLogService,
                Clock clock
        ) {
            return new TransactionService(
                    transactionMapper,
                    categoryService,
                    paymentMethodService,
                    onlinePlatformService,
                    imageService,
                    mock(TransactionRecommendationService.class),
                    mock(TransactionAiRecommendationService.class),
                    cacheService,
                    auditLogService,
                    clock);
        }
    }

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private PaymentMethodService paymentMethodService;
    @Autowired
    private OnlinePlatformService onlinePlatformService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "ALTER TABLE categories ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE categories ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE payment_methods ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE payment_methods ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE online_platforms ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE online_platforms ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS business_audit_logs (
                  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  action VARCHAR(64) NOT NULL,
                  target_type VARCHAR(32) NOT NULL,
                  target_id BIGINT,
                  source VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  request_id VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("DELETE FROM business_audit_logs");
        jdbcTemplate.update("DELETE FROM transaction_images");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM online_platforms");
        jdbcTemplate.update("DELETE FROM payment_methods");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname) VALUES (?, ?, ?, ?)",
                USER_ID, "restore-user", "hash", "Restore");
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type, icon, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CATEGORY_ID, USER_ID, "餐饮", "EXPENSE", "shop-o", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO payment_methods (id, user_id, name, sort_order, deleted) VALUES (?, ?, ?, ?, ?)",
                PAYMENT_METHOD_ID, USER_ID, "微信", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO online_platforms (id, user_id, name, sort_order, deleted) VALUES (?, ?, ?, ?, ?)",
                PLATFORM_ID, USER_ID, "美团", 10, 0);
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                  id, user_id, type, item_name, amount, occurred_at, channel,
                  online_app, online_platform_id, payment_method_id,
                  payment_method_name, category_id, deleted, trashed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TRANSACTION_ID,
                USER_ID,
                "EXPENSE",
                "午餐",
                "12.50",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 12, 0)),
                "ONLINE",
                "美团",
                PLATFORM_ID,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                0,
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 29, 18, 0)));
    }

    @Test
    void restoreRejectsCategoryTypeChangedAfterTransactionEnteredTrash() {
        categoryService.update(
                USER_ID,
                CATEGORY_ID,
                new CategoryRequest("餐饮", "INCOME", "shop-o", 10, false));

        assertThatThrownBy(() ->
                transactionService.restore(USER_ID, TRANSACTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("分类类型与记录类型不一致，无法恢复");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT trashed_at FROM transactions WHERE id = ?",
                Timestamp.class,
                TRANSACTION_ID)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM business_audit_logs
                WHERE target_id = ? AND action = 'TRANSACTION_RESTORE'
                """,
                Long.class,
                TRANSACTION_ID)).isZero();
    }

    @Test
    void concurrentCategoryDeleteAndRestoreCannotBothSucceed() throws Exception {
        assertConcurrentReferenceMutationSafe(
                "categories",
                CATEGORY_ID,
                () -> categoryService.delete(USER_ID, CATEGORY_ID));
    }

    @Test
    void concurrentCategoryTypeChangeAndRestoreCannotBothSucceed() throws Exception {
        assertConcurrentReferenceMutationSafe(
                "categories",
                CATEGORY_ID,
                () -> categoryService.update(
                        USER_ID,
                        CATEGORY_ID,
                        new CategoryRequest(
                                "餐饮", "INCOME", "shop-o", 10, false)));
    }

    @Test
    void concurrentPaymentMethodDeleteAndRestoreCannotBothSucceed()
            throws Exception {
        assertConcurrentReferenceMutationSafe(
                "payment_methods",
                PAYMENT_METHOD_ID,
                () -> paymentMethodService.delete(USER_ID, PAYMENT_METHOD_ID));
    }

    @Test
    void concurrentOnlinePlatformDeleteAndRestoreCannotBothSucceed()
            throws Exception {
        assertConcurrentReferenceMutationSafe(
                "online_platforms",
                PLATFORM_ID,
                () -> onlinePlatformService.delete(USER_ID, PLATFORM_ID));
    }

    private void assertConcurrentReferenceMutationSafe(
            String table,
            Long referenceId,
            Runnable mutation
    ) throws Exception {
        CountDownLatch referenceLocked = new CountDownLatch(1);
        CountDownLatch releaseReference = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try {
            Future<?> lockHolder = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT id FROM " + table
                                        + " WHERE id = ? AND user_id = ? AND deleted = 0 FOR UPDATE",
                                Long.class,
                                referenceId,
                                USER_ID);
                        referenceLocked.countDown();
                        await(releaseReference);
                    }));
            assertThat(referenceLocked.await(2, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> mutationResult =
                    executor.submit(() -> succeeds(mutation));
            assertThatThrownBy(() ->
                    mutationResult.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            Future<Boolean> restoreResult = executor.submit(() ->
                    succeeds(() ->
                            transactionService.restore(
                                    USER_ID, TRANSACTION_ID)));
            assertThatThrownBy(() ->
                    restoreResult.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            releaseReference.countDown();

            lockHolder.get(2, TimeUnit.SECONDS);
            boolean mutationSucceeded =
                    mutationResult.get(2, TimeUnit.SECONDS);
            boolean restoreSucceeded =
                    restoreResult.get(2, TimeUnit.SECONDS);
            assertThat((mutationSucceeded ? 1 : 0)
                    + (restoreSucceeded ? 1 : 0)).isEqualTo(1);
            assertActiveTransactionReferencesRemainValid();
        } finally {
            releaseReference.countDown();
            executor.shutdownNow();
        }
    }

    private void assertActiveTransactionReferencesRemainValid() {
        Long activeCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transactions t
                INNER JOIN categories c
                  ON c.id = t.category_id
                 AND c.user_id = t.user_id
                 AND c.deleted = 0
                 AND c.type = t.type
                INNER JOIN payment_methods pm
                  ON pm.id = t.payment_method_id
                 AND pm.user_id = t.user_id
                 AND pm.deleted = 0
                LEFT JOIN online_platforms op
                  ON op.id = t.online_platform_id
                 AND op.user_id = t.user_id
                 AND op.deleted = 0
                WHERE t.id = ?
                  AND t.user_id = ?
                  AND t.deleted = 0
                  AND t.trashed_at IS NULL
                  AND (t.online_platform_id IS NULL OR op.id IS NOT NULL)
                """,
                Long.class,
                TRANSACTION_ID,
                USER_ID);
        Long rawActiveCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transactions
                WHERE id = ? AND user_id = ? AND deleted = 0
                  AND trashed_at IS NULL
                """,
                Long.class,
                TRANSACTION_ID,
                USER_ID);
        assertThat(activeCount).isEqualTo(rawActiveCount);
    }

    private static boolean succeeds(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待行锁释放超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待行锁释放被中断", ex);
        }
    }
}
