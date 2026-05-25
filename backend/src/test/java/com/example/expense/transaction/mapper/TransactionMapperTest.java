package com.example.expense.transaction.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TransactionMapperTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class TransactionMapperTest {
    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 2002L;
    private static final Long EXPENSE_CATEGORY_ID = 10L;
    private static final Long INCOME_CATEGORY_ID = 20L;
    private static final Long WECHAT_METHOD_ID = 30L;
    private static final Long CASH_METHOD_ID = 31L;
    private static final LocalDateTime DAY_14_NOON = LocalDateTime.of(2026, 5, 14, 12, 0);
    private static final LocalDateTime DAY_13_MORNING = LocalDateTime.of(2026, 5, 13, 9, 0);
    private static final LocalDateTime DAY_14_MID = LocalDateTime.of(2026, 5, 14, 11, 0);

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.example.expense.transaction.mapper")
    static class TestApplication {
    }

    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM transaction_images");
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM payment_methods");
        jdbcTemplate.update("DELETE FROM categories");
        jdbcTemplate.update("DELETE FROM users");

        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname) VALUES (?, ?, ?, ?)",
                USER_ID, "demo", "hash", "Demo");
        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, nickname) VALUES (?, ?, ?, ?)",
                OTHER_USER_ID, "other", "hash", "Other");

        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?)",
                EXPENSE_CATEGORY_ID, USER_ID, "餐饮", "EXPENSE", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?)",
                INCOME_CATEGORY_ID, USER_ID, "工资", "INCOME", 20, 0);

        jdbcTemplate.update(
                "INSERT INTO payment_methods (id, user_id, name, sort_order, deleted) VALUES (?, ?, ?, ?, ?)",
                WECHAT_METHOD_ID, USER_ID, "微信", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO payment_methods (id, user_id, name, sort_order, deleted) VALUES (?, ?, ?, ?, ?)",
                CASH_METHOD_ID, USER_ID, "现金", 20, 1);

        insertTransaction(
                100L,
                USER_ID,
                "EXPENSE",
                "午餐",
                new BigDecimal("12.50"),
                DAY_14_NOON,
                "OFFLINE",
                "",
                "公司",
                WECHAT_METHOD_ID,
                "微信",
                EXPENSE_CATEGORY_ID,
                "午饭");
        insertTransaction(
                101L,
                USER_ID,
                "EXPENSE",
                "下午茶",
                new BigDecimal("8.00"),
                DAY_14_NOON,
                "ONLINE",
                "美团",
                "",
                CASH_METHOD_ID,
                "现金-旧名",
                EXPENSE_CATEGORY_ID,
                "咖啡");
        insertTransaction(
                102L,
                USER_ID,
                "INCOME",
                "",
                new BigDecimal("5000.00"),
                DAY_13_MORNING,
                "ONLINE",
                "银行",
                "",
                CASH_METHOD_ID,
                "现金",
                INCOME_CATEGORY_ID,
                "");
        insertTransaction(
                103L,
                OTHER_USER_ID,
                "EXPENSE",
                "外卖",
                new BigDecimal("30.00"),
                DAY_14_MID,
                "OFFLINE",
                "",
                "他人公司",
                WECHAT_METHOD_ID,
                "微信",
                EXPENSE_CATEGORY_ID,
                "他人");
    }

    @Test
    void selectRecordsAppliesFiltersOrderingAndPagination() {
        assertThat(transactionMapper.countRecords(USER_ID, "EXPENSE", null, null, null, null, null, null))
                .isEqualTo(2L);
        assertThat(transactionMapper.countRecords(OTHER_USER_ID, null, null, null, null, null, null, null))
                .isEqualTo(1L);

        List<TransactionResponse> secondPage = transactionMapper.selectRecords(
                USER_ID,
                "EXPENSE",
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                1L);
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getId()).isEqualTo(100L);
        assertThat(secondPage.get(0).getItemName()).isEqualTo("午餐");
        assertThat(secondPage.get(0).getPaymentMethodName()).isEqualTo("微信");

        List<TransactionResponse> keywordRows = transactionMapper.selectRecords(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                "美团",
                10,
                0L);
        assertThat(keywordRows).extracting(TransactionResponse::getId).containsExactly(101L);
        assertThat(keywordRows.get(0).getPaymentMethodName()).isEqualTo("现金-旧名");
    }

    @Test
    void selectRecordFallsBackToStoredValuesWhenJoinedRowsAreUnavailable() {
        TransactionResponse response = transactionMapper.selectRecord(USER_ID, 102L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(102L);
        assertThat(response.getItemName()).isEmpty();
        assertThat(response.getPaymentMethodName()).isEqualTo("现金");
        assertThat(response.getCategoryName()).isEqualTo("工资");
        assertThat(response.getChannel()).isEqualTo("ONLINE");
    }

    @Test
    void selectDayCardsAndOptionsAggregateByDate() {
        LocalDateTime startAt = LocalDate.of(2026, 5, 13).atStartOfDay();
        LocalDateTime endAt = LocalDate.of(2026, 5, 15).atStartOfDay();

        List<TransactionDayCardResponse> cards = transactionMapper.selectDayCards(
                USER_ID,
                null,
                startAt,
                endAt,
                null,
                null,
                null,
                null,
                10,
                0L);
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(cards.get(0).getTotalExpense()).isEqualByComparingTo("20.50");
        assertThat(cards.get(0).getTotalIncome()).isEqualByComparingTo("0");
        assertThat(cards.get(0).getTransactionCount()).isEqualTo(2L);
        assertThat(cards.get(1).getDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(cards.get(1).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(cards.get(1).getTotalIncome()).isEqualByComparingTo("5000.00");
        assertThat(cards.get(1).getTransactionCount()).isEqualTo(1L);

        assertThat(transactionMapper.countRecordDays(USER_ID, null, startAt, endAt, null, null, null, null))
                .isEqualTo(2L);

        List<TransactionDayOptionResponse> options = transactionMapper.selectDayOptions(
                USER_ID,
                null,
                startAt,
                endAt,
                null,
                null,
                null,
                null);
        assertThat(options).hasSize(2);
        assertThat(options.get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 14));
        assertThat(options.get(0).getTotalExpense()).isEqualByComparingTo("20.50");
        assertThat(options.get(0).getTotalIncome()).isEqualByComparingTo("0");
        assertThat(options.get(1).getDate()).isEqualTo(LocalDate.of(2026, 5, 13));
        assertThat(options.get(1).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(options.get(1).getTotalIncome()).isEqualByComparingTo("5000.00");
    }

    private void insertTransaction(
            Long id,
            Long userId,
            String type,
            String itemName,
            BigDecimal amount,
            LocalDateTime occurredAt,
            String channel,
            String onlineApp,
            String offlinePlace,
            Long paymentMethodId,
            String paymentMethodName,
            Long categoryId,
            String note
    ) {
        jdbcTemplate.update(
                "INSERT INTO transactions (id, user_id, type, item_name, amount, occurred_at, channel, online_app, offline_place, payment_method_id, payment_method_name, category_id, note, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                type,
                itemName,
                amount,
                Timestamp.valueOf(occurredAt),
                channel,
                onlineApp,
                offlinePlace,
                paymentMethodId,
                paymentMethodName,
                categoryId,
                note,
                0);
    }
}
