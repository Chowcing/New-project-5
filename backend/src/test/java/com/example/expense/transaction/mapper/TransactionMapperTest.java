package com.example.expense.transaction.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.admin.mapper.AdminMapper;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.mapper.StatisticsMapper;
import com.example.expense.transaction.dto.ExpiredTrashCandidate;
import com.example.expense.transaction.dto.TransactionRecommendationAggregateRow;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TrashedTransactionResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.SqlSessionFactory;
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
    @MapperScan({
            "com.example.expense.transaction.mapper",
            "com.example.expense.statistics.mapper",
            "com.example.expense.admin.mapper"
    })
    static class TestApplication {
    }

    @Autowired
    private TransactionMapper transactionMapper;
    @Autowired
    private StatisticsMapper statisticsMapper;
    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;

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
                "INSERT INTO categories (id, user_id, name, type, icon, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                EXPENSE_CATEGORY_ID, USER_ID, "餐饮", "EXPENSE", "shop-o", 10, 0);
        jdbcTemplate.update(
                "INSERT INTO categories (id, user_id, name, type, icon, sort_order, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                INCOME_CATEGORY_ID, USER_ID, "工资", "INCOME", "balance-pay", 20, 0);

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
        insertTransaction(
                104L,
                USER_ID,
                "EXPENSE",
                "回收站午餐",
                new BigDecimal("66.00"),
                DAY_14_MID,
                "OFFLINE",
                "",
                "食堂",
                WECHAT_METHOD_ID,
                "微信",
                EXPENSE_CATEGORY_ID,
                "不应计入");
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 15, 9, 0)),
                104L);
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
        assertThat(secondPage.get(0).getCategoryIcon()).isEqualTo("shop-o");

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
        assertThat(response.getCategoryIcon()).isEqualTo("balance-pay");
        assertThat(response.getChannel()).isEqualTo("ONLINE");
    }

    @Test
    void trashTransitionsAreOwnedAndListContainsNoImageUrl() {
        assertThat(transactionMapper.moveToTrash(
                USER_ID, 100L, LocalDateTime.of(2026, 5, 20, 8, 30)))
                .isEqualTo(1);
        assertThat(transactionMapper.moveToTrash(
                USER_ID, 100L, LocalDateTime.of(2026, 5, 20, 8, 31)))
                .isZero();
        assertThat(transactionMapper.moveToTrash(
                OTHER_USER_ID, 100L, LocalDateTime.of(2026, 5, 20, 8, 31)))
                .isZero();
        assertThat(transactionMapper.countTrashedRecords(USER_ID)).isEqualTo(2L);

        List<TrashedTransactionResponse> trashed = transactionMapper.selectTrashedRecords(USER_ID, 20, 0L);
        assertThat(trashed)
                .extracting(TrashedTransactionResponse::getId)
                .containsExactly(100L, 104L);
        assertThat(TrashedTransactionResponse.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("images");
        assertThat(transactionMapper.restoreFromTrash(OTHER_USER_ID, 100L)).isZero();
        assertThat(transactionMapper.restoreFromTrash(USER_ID, 100L)).isEqualTo(1);
        assertThat(transactionMapper.softDeleteActive(OTHER_USER_ID, 100L)).isZero();
        assertThat(transactionMapper.softDeleteActive(USER_ID, 100L)).isEqualTo(1);
        assertThat(transactionMapper.softDeleteTrashed(OTHER_USER_ID, 104L)).isZero();
        assertThat(transactionMapper.softDeleteTrashed(USER_ID, 104L)).isEqualTo(1);
    }

    @Test
    void selectExpiredTrashCandidatesUsesEachUsersFixedDayBoundaryAndIdCursor() {
        LocalDateTime runAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        jdbcTemplate.update(
                "UPDATE users SET trash_retention_days = ? WHERE id = ?",
                30,
                USER_ID);
        jdbcTemplate.update(
                "UPDATE users SET trash_retention_days = ? WHERE id = ?",
                7,
                OTHER_USER_ID);
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 29, 3, 0)),
                104L);

        insertTransaction(
                201L, USER_ID, "EXPENSE", "边界一", new BigDecimal("1.00"),
                DAY_14_NOON, "OFFLINE", "", "公司", WECHAT_METHOD_ID,
                "微信", EXPENSE_CATEGORY_ID, "");
        insertTransaction(
                202L, OTHER_USER_ID, "EXPENSE", "边界二", new BigDecimal("2.00"),
                DAY_14_NOON, "OFFLINE", "", "公司", WECHAT_METHOD_ID,
                "微信", EXPENSE_CATEGORY_ID, "");
        insertTransaction(
                203L, USER_ID, "EXPENSE", "未到期", new BigDecimal("3.00"),
                DAY_14_NOON, "OFFLINE", "", "公司", WECHAT_METHOD_ID,
                "微信", EXPENSE_CATEGORY_ID, "");
        insertTransaction(
                204L, USER_ID, "EXPENSE", "正常记录", new BigDecimal("4.00"),
                DAY_14_NOON, "OFFLINE", "", "公司", WECHAT_METHOD_ID,
                "微信", EXPENSE_CATEGORY_ID, "");
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 30, 3, 0)),
                201L);
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 23, 3, 0)),
                202L);
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 30, 3, 1)),
                203L);

        List<ExpiredTrashCandidate> candidates =
                transactionMapper.selectExpiredTrashCandidates(runAt, 0L, 200);

        assertThat(candidates)
                .extracting(ExpiredTrashCandidate::id)
                .containsExactly(201L, 202L)
                .doesNotContain(203L, 204L);
        assertThat(candidates)
                .extracting(ExpiredTrashCandidate::userId)
                .containsExactly(USER_ID, OTHER_USER_ID);
        assertThat(transactionMapper.selectExpiredTrashCandidates(runAt, 201L, 200))
                .extracting(ExpiredTrashCandidate::id)
                .containsExactly(202L);
        assertThat(transactionMapper.selectExpiredTrashForUpdate(
                USER_ID, 201L, runAt))
                .isEqualTo(new ExpiredTrashCandidate(201L, USER_ID));
        assertThat(transactionMapper.selectExpiredTrashForUpdate(
                USER_ID, 204L, runAt))
                .isNull();
    }

    @Test
    void selectExpiredTrashForUpdateRechecksCurrentRetentionAndLocksMatchingRow() {
        LocalDateTime runAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        jdbcTemplate.update(
                "UPDATE users SET trash_retention_days = ? WHERE id = ?",
                31,
                USER_ID);
        jdbcTemplate.update(
                "UPDATE transactions SET trashed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 6, 30, 3, 0)),
                104L);

        assertThat(transactionMapper.selectExpiredTrashForUpdate(
                OTHER_USER_ID, 104L, runAt))
                .isNull();
        assertThat(transactionMapper.selectExpiredTrashForUpdate(
                USER_ID, 104L, runAt))
                .isNull();

        MappedStatement statement = sqlSessionFactory.getConfiguration()
                .getMappedStatement(
                        TransactionMapper.class.getName()
                                + ".selectExpiredTrashForUpdate");
        String sql = statement.getBoundSql(Map.of(
                        "userId", USER_ID,
                        "id", 104L,
                        "runAt", runAt))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();
        assertThat(sql).endsWith("FOR UPDATE");
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

    @Test
    void selectRecommendationAggregatesUsesAllHistoryAndLatestVariantPayload() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 14, 12, 30);
        insertTransaction(
                106L,
                USER_ID,
                "EXPENSE",
                "午餐",
                new BigDecimal("15.00"),
                contextAt.minusDays(210).withHour(12).withMinute(15),
                "OFFLINE",
                "",
                "公司",
                WECHAT_METHOD_ID,
                "微信",
                EXPENSE_CATEGORY_ID,
                "旧午饭");

        List<TransactionRecommendationAggregateRow> rows =
                transactionMapper.selectRecommendationAggregates(
                        USER_ID, "EXPENSE", null, contextAt, 12 * 60 + 30, 5);

        assertThat(rows).hasSize(1);
        TransactionRecommendationAggregateRow row = rows.get(0);
        assertThat(row.getLatestTransactionId()).isEqualTo(100L);
        assertThat(row.getOccurrenceCount()).isEqualTo(2L);
        assertThat(row.getTimeWindowHitCount()).isEqualTo(2L);
        assertThat(row.getSameWeekdayCount()).isEqualTo(2L);
        assertThat(row.getSameDayTypeCount()).isEqualTo(2L);
        assertThat(row.getMinAmount()).isEqualByComparingTo("12.50");
        assertThat(row.getMaxAmount()).isEqualByComparingTo("15.00");
        assertThat(row.getAmount()).isEqualByComparingTo("12.50");
        assertThat(row.getNote()).isEqualTo("午饭");
    }

    @Test
    void selectRecommendationAggregatesAllowsNullPlatformAndAppliesTypeChannelAndUpperBound() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 14, 12, 30);
        insertTransaction(
                105L,
                USER_ID,
                "INCOME",
                "",
                new BigDecimal("6000.00"),
                contextAt.minusDays(1),
                "ONLINE",
                "银行",
                "",
                WECHAT_METHOD_ID,
                "微信",
                INCOME_CATEGORY_ID,
                "");

        List<TransactionRecommendationAggregateRow> rows =
                transactionMapper.selectRecommendationAggregates(
                        USER_ID, "INCOME", "ONLINE", contextAt, 12 * 60 + 30, 5);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getLatestTransactionId()).isEqualTo(105L);
            assertThat(row.getOnlinePlatformId()).isNull();
            assertThat(row.getChannel()).isEqualTo("ONLINE");
        });
    }

    @Test
    void trashedRecordsAreExcludedFromNormalTransactionStatisticsRecommendationAndAdminQueries() {
        assertThat(transactionMapper.countRecords(
                USER_ID, null, null, null, null, null, null, null))
                .isEqualTo(3L);
        assertThat(transactionMapper.selectRecord(USER_ID, 104L)).isNull();
        assertThat(transactionMapper.selectRecommendationAggregates(
                USER_ID,
                null,
                null,
                LocalDateTime.of(2026, 5, 20, 12, 0),
                720,
                4))
                .noneMatch(row -> row.getLatestTransactionId().equals(104L));

        MonthlyTotals totals = statisticsMapper.selectMonthlyTotals(
                USER_ID,
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(totals.getTransactionCount()).isEqualTo(3L);
        assertThat(adminMapper.countTransactions(
                USER_ID, null, null, null, null, null))
                .isEqualTo(3L);
        assertThat(adminMapper.selectTransactionDetail(104L)).isNull();
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
