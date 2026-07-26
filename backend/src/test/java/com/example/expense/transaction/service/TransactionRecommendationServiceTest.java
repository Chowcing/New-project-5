package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.QuickEntryRecommendationsResponse;
import com.example.expense.transaction.dto.TransactionRecommendationAggregateRow;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionRecommendationServiceTest {
    private static final Long USER_ID = 1001L;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private OnlinePlatformService onlinePlatformService;

    private TransactionRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new TransactionRecommendationService(
                transactionMapper,
                categoryService,
                paymentMethodService,
                onlinePlatformService,
                CLOCK
        );
    }

    @Test
    void recommendationsMergeSceneVariantsAndUseLatestCompleteTemplate() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionRecommendationAggregateRow olderWechat = aggregate(
                11L, "打 车", 2001L, 3001L, 4001L, null,
                "28.00", now.minusDays(10), 4, 3, 2, 4, "25.00", "30.00");
        TransactionRecommendationAggregateRow latestAlipay = aggregate(
                12L, "打车！", 2001L, 3002L, 4002L, null,
                "32.00", now.minusDays(1), 2, 2, 1, 2, "31.00", "32.00");
        TransactionRecommendationAggregateRow otherCategory = aggregate(
                13L, "打车", 2002L, 3001L, null, "公司",
                "18.00", now.minusDays(2), 1, 1, 1, 1, "18.00", "18.00");
        whenAggregateRows("EXPENSE", null, now,
                List.of(olderWechat, latestAlipay, otherCategory));

        List<TransactionTemplateResponse> templates =
                service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).hasSize(2);
        TransactionTemplateResponse merged = templates.stream()
                .filter(template -> template.categoryId() == 2001L)
                .findFirst()
                .orElseThrow();
        assertThat(merged.paymentMethodId()).isEqualTo(3002L);
        assertThat(merged.onlinePlatformId()).isEqualTo(4002L);
        assertThat(merged.amount()).isEqualByComparingTo("32.00");
        assertThat(merged.reason()).contains("历史出现 6 次", "金额参考最近记录");
    }

    @Test
    void recommendationsRequireRepeatedHabitEvidenceAndPreferRecentIntent() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionRecommendationAggregateRow single = aggregate(
                21L, "咖啡", 2002L, 3001L, null, "楼下",
                "18.00", now.minusDays(1), 1, 1, 1, 1, "18.00", "18.00");
        TransactionRecommendationAggregateRow repeated = aggregate(
                22L, "早餐", 2002L, 3001L, null, "食堂",
                "12.00", now.minusDays(2), 3, 2, 2, 3, "10.00", "12.00");
        TransactionRecommendationAggregateRow stale = aggregate(
                23L, "旧午餐", 2002L, 3001L, null, "旧食堂",
                "30.00", now.minusDays(365), 20, 20, 20, 20, "30.00", "30.00");
        whenAggregateRows("EXPENSE", null, now, List.of(single, repeated, stale));

        List<TransactionTemplateResponse> templates =
                service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).isNotEmpty();
        assertThat(templates.get(0).itemName()).isEqualTo("早餐");
        assertThat(templates.stream()
                .filter(template -> "咖啡".equals(template.itemName()))
                .findFirst().orElseThrow().reason())
                .doesNotContain("常在当前时段", "同一星期习惯");
        assertThat(templates.stream()
                .filter(template -> "早餐".equals(template.itemName()))
                .findFirst().orElseThrow().reason())
                .contains("常在当前时段记录", "同一星期习惯");
        assertThat(templates.indexOf(templates.stream()
                .filter(template -> "旧午餐".equals(template.itemName()))
                .findFirst().orElseThrow())).isGreaterThan(0);
    }

    @Test
    void contextRecommendationsDoNotMatchBlankItemToArbitraryQuery() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionRecommendationAggregateRow blankItem = aggregate(
                31L, null, 2001L, 3001L, null, "公司",
                "20.00", now.minusDays(1), 5, 5, 5, 5, "20.00", "20.00");
        whenAggregateRows("EXPENSE", null, now, List.of(blankItem));

        List<TransactionTemplateResponse> templates =
                service.recommendContextTemplates(
                        USER_ID, "打车", "EXPENSE", null, now, 3);

        assertThat(templates).isEmpty();
    }

    @Test
    void contextRecommendationsUseBestTextScoreOnceAndCapItAtOneHundredThirty() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionRecommendationAggregateRow first = aggregate(
                32L, "奶茶", 2002L, 3001L, null, "奶茶店",
                "18.00", now, 5, 0, 0, 0, "18.00", "18.00");
        TransactionRecommendationAggregateRow second = aggregate(
                33L, "奶 茶！", 2002L, 3002L, null, "另一家店",
                "20.00", now.minusMinutes(1), 4, 0, 0, 0, "20.00", "20.00");
        whenAggregateRows("EXPENSE", null, now, List.of(first, second));

        List<TransactionTemplateResponse> templates =
                service.recommendContextTemplates(
                        USER_ID, "奶茶", "EXPENSE", null, now, 3);

        assertThat(templates).singleElement().satisfies(template -> {
            assertThat(template.itemName()).isEqualTo("奶茶");
            assertThat(template.score()).isEqualTo(222.0);
        });
    }

    @Test
    void quickEntryRecommendationsPreferRecentAndPinnedOptions() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        Category pinnedCategory = category(2002L, "交通", 20, true);
        Category recentCategory = category(2001L, "餐饮", 10, false);
        PaymentMethod wechat = paymentMethod(3001L, "微信", 10);
        PaymentMethod cash = paymentMethod(3002L, "现金", 20);
        OnlinePlatform meituan = platform(4001L, "美团", 10, false);
        OnlinePlatform taobao = platform(4002L, "淘宝", 20, true);
        TransactionResponse recent = new TransactionResponse();
        recent.setId(51L);
        recent.setType("EXPENSE");
        recent.setAmount(new BigDecimal("22.00"));
        recent.setOccurredAt(now.minusMinutes(5));
        recent.setChannel("ONLINE");
        recent.setOnlineApp("美团");
        recent.setOnlinePlatformId(4001L);
        recent.setPaymentMethodId(3001L);
        recent.setCategoryId(2001L);
        when(transactionMapper.selectRecords(
                eq(USER_ID), eq("EXPENSE"), any(LocalDateTime.class),
                any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull(),
                eq(500), eq(0L)))
                .thenReturn(List.of(recent));
        whenAggregateRows("EXPENSE", null, now, List.of(aggregate(
                51L, null, 2001L, 3001L, 4001L, null,
                "22.00", now.minusMinutes(5), 1, 1, 1, 1, "22.00", "22.00")));
        when(categoryService.list(USER_ID, "EXPENSE"))
                .thenReturn(List.of(recentCategory, pinnedCategory));
        when(paymentMethodService.list(USER_ID)).thenReturn(List.of(wechat, cash));
        when(onlinePlatformService.list(USER_ID)).thenReturn(List.of(meituan, taobao));

        QuickEntryRecommendationsResponse response =
                service.recommendQuickEntry(USER_ID, "EXPENSE", 10);

        assertThat(response.categories()).extracting(Category::getName)
                .containsExactly("交通", "餐饮");
        assertThat(response.paymentMethods()).extracting(PaymentMethod::getName)
                .containsExactly("微信", "现金");
        assertThat(response.onlinePlatforms()).extracting(OnlinePlatform::getName)
                .containsExactly("淘宝", "美团");
        assertThat(response.combinations()).hasSize(1);
    }

    private void whenAggregateRows(
            String type,
            String channel,
            LocalDateTime occurredAt,
            List<TransactionRecommendationAggregateRow> rows
    ) {
        int minute = occurredAt.getHour() * 60 + occurredAt.getMinute();
        int sundayBasedDay = occurredAt.getDayOfWeek().getValue() % 7 + 1;
        when(transactionMapper.selectRecommendationAggregates(
                USER_ID, type, channel, occurredAt, minute, sundayBasedDay))
                .thenReturn(rows);
    }

    private TransactionRecommendationAggregateRow aggregate(
            long id,
            String itemName,
            long categoryId,
            long paymentMethodId,
            Long platformId,
            String offlinePlace,
            String amount,
            LocalDateTime latestAt,
            long count,
            long timeHits,
            long weekdayHits,
            long dayTypeHits,
            String minAmount,
            String maxAmount
    ) {
        TransactionRecommendationAggregateRow row =
                new TransactionRecommendationAggregateRow();
        row.setLatestTransactionId(id);
        row.setType("EXPENSE");
        row.setItemName(itemName);
        row.setAmount(new BigDecimal(amount));
        row.setLatestOccurredAt(latestAt);
        row.setChannel(platformId == null ? "OFFLINE" : "ONLINE");
        row.setOnlineApp(platformId == null ? null : "平台-" + platformId);
        row.setOnlinePlatformId(platformId);
        row.setOfflinePlace(offlinePlace);
        row.setPaymentMethodId(paymentMethodId);
        row.setPaymentMethodName(paymentMethodId == 3001L ? "微信" : "支付宝");
        row.setCategoryId(categoryId);
        row.setCategoryName(categoryId == 2001L ? "交通" : "餐饮");
        row.setNote("最近备注");
        row.setOccurrenceCount(count);
        row.setTimeWindowHitCount(timeHits);
        row.setSameWeekdayCount(weekdayHits);
        row.setSameDayTypeCount(dayTypeHits);
        row.setMinAmount(new BigDecimal(minAmount));
        row.setMaxAmount(new BigDecimal(maxAmount));
        return row;
    }

    private Category category(long id, String name, int sortOrder, boolean pinned) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType("EXPENSE");
        category.setSortOrder(sortOrder);
        category.setPinned(pinned);
        return category;
    }

    private PaymentMethod paymentMethod(long id, String name, int sortOrder) {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(id);
        paymentMethod.setName(name);
        paymentMethod.setSortOrder(sortOrder);
        return paymentMethod;
    }

    private OnlinePlatform platform(
            long id,
            String name,
            int sortOrder,
            boolean pinned
    ) {
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(id);
        platform.setName(name);
        platform.setSortOrder(sortOrder);
        platform.setPinned(pinned);
        return platform;
    }
}
