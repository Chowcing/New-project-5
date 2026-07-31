package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.expense.admin.config.AdminProperties;
import com.example.expense.admin.dto.AdminReasonRequest;
import com.example.expense.admin.mapper.AdminAuditLogMapper;
import com.example.expense.admin.mapper.AdminMapper;
import com.example.expense.admin.service.AdminService;
import com.example.expense.auth.service.AuthService;
import com.example.expense.businessaudit.mapper.BusinessAuditLogMapper;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.config.StorageProperties;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.mapper.TransactionImageMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import com.example.expense.user.mapper.UserMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = TransactionImageLifecycleConcurrencyIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TransactionImageLifecycleConcurrencyIntegrationTest {
    private static final Long ADMIN_USER_ID = 9001L;
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 10L;
    private static final Long PAYMENT_METHOD_ID = 30L;
    private static final Long TRANSACTION_ID = 201L;
    private static final byte[] JPEG_BYTES =
            new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};

    @TempDir
    static Path tempDir;
    private static Path configuredImageRoot;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.businessaudit.mapper",
            "com.example.expense.admin.mapper"
    })
    static class TestApplication {
        @Bean
        Clock appClock() {
            return Clock.fixed(
                    Instant.parse("2027-07-30T00:00:00Z"),
                    ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        StorageProperties storageProperties() {
            StorageProperties properties = new StorageProperties();
            configuredImageRoot = tempDir.resolve("transaction-images");
            properties.setTransactionImageDir(configuredImageRoot.toString());
            properties.setTransactionImageRetentionDays(0);
            return properties;
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
        AdminService adminService(
                TransactionMapper transactionMapper,
                TransactionService transactionService,
                TransactionImageService transactionImageService,
                AdminAuditLogMapper adminAuditLogMapper,
                Clock clock
        ) {
            return new AdminService(
                    mock(AdminMapper.class),
                    mock(UserMapper.class),
                    mock(AuthService.class),
                    transactionMapper,
                    transactionService,
                    transactionImageService,
                    adminAuditLogMapper,
                    new AdminProperties(),
                    clock);
        }
    }

    @Autowired
    private TransactionImageService transactionImageService;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private AdminService adminService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "ALTER TABLE transactions ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute(
                "ALTER TABLE transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
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
        jdbcTemplate.update("DELETE FROM admin_audit_logs");
        jdbcTemplate.update("DELETE FROM transaction_images");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM online_platforms");
        jdbcTemplate.update("DELETE FROM payment_methods");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname) VALUES (?, ?, ?, ?)",
                ADMIN_USER_ID, "image-admin", "hash", "Admin");
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname) VALUES (?, ?, ?, ?)",
                USER_ID, "image-user", "hash", "Image");
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
                  category_id, deleted, trashed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TRANSACTION_ID,
                USER_ID,
                "EXPENSE",
                "午餐",
                "12.50",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 12, 0)),
                "OFFLINE",
                "公司",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                0,
                null);
    }

    @Test
    void slowAppendAndPermanentDeleteLeaveNoActiveOrPhysicalOrphan()
            throws Exception {
        runSlowUploadAgainstDeletion(() -> {
            transactionService.delete(USER_ID, TRANSACTION_ID);
            transactionService.permanentlyDelete(USER_ID, TRANSACTION_ID);
        });
    }

    @Test
    void slowAppendAndAdminDeleteLeaveNoActiveOrPhysicalOrphan()
            throws Exception {
        runSlowUploadAgainstDeletion(() ->
                adminService.deleteTransaction(
                        ADMIN_USER_ID,
                        TRANSACTION_ID,
                        new AdminReasonRequest("测试并发删除")));
    }

    private void runSlowUploadAgainstDeletion(Runnable deletion)
            throws Exception {
        CountDownLatch slowWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        SlowMultipartFile file =
                new SlowMultipartFile(slowWriteStarted, releaseWrite);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> upload = executor.submit(() ->
                    transactionImageService.appendImages(
                            USER_ID, TRANSACTION_ID, List.of(file)));
            assertThat(slowWriteStarted.await(2, TimeUnit.SECONDS)).isTrue();

            Future<?> delete = executor.submit(deletion);
            assertThatThrownBy(() ->
                    delete.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseWrite.countDown();
            upload.get(2, TimeUnit.SECONDS);
            delete.get(2, TimeUnit.SECONDS);
        } finally {
            releaseWrite.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM transactions WHERE id = ?",
                Integer.class,
                TRANSACTION_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transaction_images
                WHERE transaction_id = ? AND user_id = ? AND deleted = 0
                """,
                Long.class,
                TRANSACTION_ID,
                USER_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT physical_deleted_at FROM transaction_images
                WHERE transaction_id = ? AND user_id = ? AND deleted = 1
                """,
                Timestamp.class,
                TRANSACTION_ID,
                USER_ID)).isNull();

        assertThat(transactionImageService.cleanupDeletedPhysicalFiles())
                .isEqualTo(1);
        assertThat(countPhysicalFiles()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transaction_images
                WHERE transaction_id = ?
                  AND deleted = 1
                  AND physical_deleted_at IS NOT NULL
                """,
                Long.class,
                TRANSACTION_ID)).isEqualTo(1L);
    }

    private long countPhysicalFiles() throws IOException {
        Path root = configuredImageRoot;
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private static final class SlowMultipartFile extends MockMultipartFile {
        private final CountDownLatch slowWriteStarted;
        private final CountDownLatch releaseWrite;
        private final AtomicInteger openCount = new AtomicInteger();

        private SlowMultipartFile(
                CountDownLatch slowWriteStarted,
                CountDownLatch releaseWrite
        ) {
            super("images", "receipt.jpg", "image/jpeg", JPEG_BYTES);
            this.slowWriteStarted = slowWriteStarted;
            this.releaseWrite = releaseWrite;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (openCount.incrementAndGet() >= 2) {
                slowWriteStarted.countDown();
                await(releaseWrite);
            }
            return super.getInputStream();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待慢上传释放超时");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待慢上传被中断", ex);
            }
        }
    }
}
