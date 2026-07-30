package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.expense.businessaudit.mapper.BusinessAuditLogMapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.config.StorageProperties;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.ExpiredTrashCandidate;
import com.example.expense.transaction.dto.TrashCleanupResult;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        classes = TransactionTrashCleanupTransactionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransactionTrashCleanupTransactionIntegrationTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_METHOD_ID = 30L;
    private static final Long TRANSACTION_ID = 201L;
    private static final LocalDateTime RUN_AT =
            LocalDateTime.of(2026, 7, 30, 3, 0);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.businessaudit.mapper"
    })
    static class TestApplication {
        @Bean
        Clock appClock() {
            return Clock.fixed(
                    Instant.parse("2026-07-29T19:00:00Z"),
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
                TransactionImageService imageService,
                CacheInvalidationService cacheService,
                BusinessAuditLogService auditLogService,
                Clock clock
        ) {
            return new TransactionService(
                    transactionMapper,
                    mock(CategoryService.class),
                    mock(PaymentMethodService.class),
                    mock(OnlinePlatformService.class),
                    imageService,
                    mock(TransactionRecommendationService.class),
                    mock(TransactionAiRecommendationService.class),
                    cacheService,
                    auditLogService,
                    clock);
        }

        @Bean
        TransactionTrashCleanupService transactionTrashCleanupService(
                TransactionMapper transactionMapper,
                TransactionService transactionService,
                Clock clock
        ) {
            return new TransactionTrashCleanupService(
                    transactionMapper, transactionService, clock);
        }
    }

    @Autowired
    private TransactionTrashCleanupService cleanupService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void setUp() {
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
        jdbcTemplate.update("DELETE FROM payment_methods");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname, trash_retention_days) VALUES (?, ?, ?, ?, ?)",
                USER_ID, "cleanup-user", "hash", "Cleanup", 30);
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type, icon, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CATEGORY_ID, USER_ID, "餐饮", "EXPENSE", "shop-o", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO payment_methods (id, user_id, name, sort_order, deleted) VALUES (?, ?, ?, ?, ?)",
                PAYMENT_METHOD_ID, USER_ID, "微信", 10, 0);
        jdbcTemplate.update(
                """
                INSERT INTO transactions (
                  id, user_id, type, item_name, amount, occurred_at, channel,
                  offline_place, payment_method_id, payment_method_name,
                  category_id, note, deleted, trashed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TRANSACTION_ID,
                USER_ID,
                "EXPENSE",
                "午餐",
                "12.50",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 14, 12, 0)),
                "OFFLINE",
                "公司",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "测试备注",
                0,
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 30, 3, 0)));
        jdbcTemplate.update(
                """
                INSERT INTO transaction_images (
                  user_id, transaction_id, original_filename, stored_filename,
                  relative_path, content_type, size_bytes, sort_order, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                USER_ID,
                TRANSACTION_ID,
                "receipt.jpg",
                "stored.jpg",
                "2026-05-14/user-1001/stored.jpg",
                "image/jpeg",
                128L,
                1,
                0);
        clearInvocations(cacheInvalidationService, redisTemplate);
    }

    @Test
    void cleanupRollsBackTransactionImageAuditAndAfterCommitCacheOnFailure() {
        AtomicBoolean transactionWasActive = new AtomicBoolean();
        AtomicBoolean uncommittedChangesWereVisible = new AtomicBoolean();
        doAnswer(invocation -> {
            transactionWasActive.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted FROM transactions WHERE id = ?",
                    Integer.class,
                    TRANSACTION_ID)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT deleted FROM transaction_images WHERE transaction_id = ?",
                    Integer.class,
                    TRANSACTION_ID)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM business_audit_logs WHERE target_id = ?",
                    Long.class,
                    TRANSACTION_ID)).isEqualTo(1L);
            uncommittedChangesWereVisible.set(true);
            throw new IllegalStateException("注入事务故障");
        }).when(cacheInvalidationService)
                .evictRecommendationsAfterCommit(USER_ID);

        TrashCleanupResult result = cleanupService.cleanupExpired();

        assertThat(AopUtils.isAopProxy(transactionService)).isTrue();
        assertThat(transactionWasActive).isTrue();
        assertThat(uncommittedChangesWereVisible).isTrue();
        assertThat(result).isEqualTo(new TrashCleanupResult(0, 1));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM transactions WHERE id = ?",
                Integer.class,
                TRANSACTION_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT trashed_at FROM transactions WHERE id = ?",
                Timestamp.class,
                TRANSACTION_ID)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM transaction_images WHERE transaction_id = ?",
                Integer.class,
                TRANSACTION_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_audit_logs WHERE target_id = ?",
                Long.class,
                TRANSACTION_ID)).isZero();
        verify(cacheInvalidationService).evictStatisticsAfterCommit(USER_ID);
        verify(cacheInvalidationService).evictRecommendationsAfterCommit(USER_ID);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void selectExpiredTrashForUpdateBlocksThenSeesCommittedDeletion()
            throws Exception {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        assertThat(transactionMapper.selectExpiredTrashForUpdate(
                                USER_ID, TRANSACTION_ID, RUN_AT))
                                .isEqualTo(new ExpiredTrashCandidate(
                                        TRANSACTION_ID, USER_ID));
                        firstLocked.countDown();
                        await(allowFirstCommit);
                        assertThat(transactionMapper.softDeleteTrashed(
                                USER_ID, TRANSACTION_ID)).isEqualTo(1);
                    }));
            assertThat(firstLocked.await(2, TimeUnit.SECONDS)).isTrue();

            Future<ExpiredTrashCandidate> second = executor.submit(() -> {
                secondAttempting.countDown();
                return transactionTemplate.execute(status ->
                        transactionMapper.selectExpiredTrashForUpdate(
                                USER_ID, TRANSACTION_ID, RUN_AT));
            });

            assertThat(secondAttempting.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            allowFirstCommit.countDown();
            first.get(2, TimeUnit.SECONDS);
            assertThat(second.get(2, TimeUnit.SECONDS)).isNull();
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待事务提交超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待事务提交被中断", ex);
        }
    }
}
