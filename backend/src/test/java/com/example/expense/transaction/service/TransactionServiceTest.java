package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.QuickEntryRecommendationsResponse;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 2001L;
    private static final Long PAYMENT_METHOD_ID = 3001L;
    private static final Long TRANSACTION_ID = 88L;
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 5, 14, 10, 30);
    private static final LocalDate START_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 5, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 5, 14);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-27T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private OnlinePlatformService onlinePlatformService;
    @Mock
    private TransactionImageService transactionImageService;
    @Mock
    private BusinessAuditLogService businessAuditLogService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                transactionMapper,
                categoryService,
                paymentMethodService,
                onlinePlatformService,
                transactionImageService,
                CLOCK,
                null,
                businessAuditLogService
        );
    }

    @Test
    void createRejectsMissingOfflinePlace() {
        stubOwnedReferences();

        assertThatThrownBy(() -> service.create(USER_ID, request(
                "EXPENSE",
                "OFFLINE",
                null,
                "   ",
                "  午餐  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线下记录需要填写地点");

        verify(categoryService).requireOwned(USER_ID, CATEGORY_ID);
        verify(paymentMethodService).requireOwned(USER_ID, PAYMENT_METHOD_ID);
        verify(transactionMapper, never()).insert(any(ExpenseTransaction.class));
    }

    @Test
    void createRejectsMissingOnlineAppForOnlineExpense() {
        stubOwnedReferences();

        assertThatThrownBy(() -> service.create(USER_ID, request(
                "EXPENSE",
                "ONLINE",
                "   ",
                null,
                "  午餐  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("线上支出需要填写消费 APP");

        verify(categoryService).requireOwned(USER_ID, CATEGORY_ID);
        verify(paymentMethodService).requireOwned(USER_ID, PAYMENT_METHOD_ID);
        verify(transactionMapper, never()).insert(any(ExpenseTransaction.class));
    }

    @Test
    void createPersistsOfflineExpenseWithNormalizedFields() {
        Category category = ownedCategory();
        PaymentMethod paymentMethod = ownedPaymentMethod();
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(category);
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(paymentMethod);
        doAnswer(invocation -> {
            ExpenseTransaction transaction = invocation.getArgument(0);
            transaction.setId(TRANSACTION_ID);
            return 1;
        }).when(transactionMapper).insert(any(ExpenseTransaction.class));

        ExpenseTransaction saved = service.create(USER_ID, request(
                "EXPENSE",
                "OFFLINE",
                null,
                "  星巴克  ",
                "   "));

        ArgumentCaptor<ExpenseTransaction> captor = ArgumentCaptor.forClass(ExpenseTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertThat(captor.getValue()).isSameAs(saved);
        assertThat(saved.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getType()).isEqualTo("EXPENSE");
        assertThat(saved.getItemName()).isEqualTo("午餐");
        assertThat(saved.getAmount()).isEqualByComparingTo("12.50");
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.getChannel()).isEqualTo("OFFLINE");
        assertThat(saved.getOnlineApp()).isNull();
        assertThat(saved.getOfflinePlace()).isEqualTo("星巴克");
        assertThat(saved.getPaymentMethodId()).isEqualTo(PAYMENT_METHOD_ID);
        assertThat(saved.getPaymentMethodName()).isEqualTo("微信");
        assertThat(saved.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(saved.getNote()).isNull();
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_CREATE", "TRANSACTION", TRANSACTION_ID, "USER");
    }

    @Test
    void updatePersistsOnlineIncomeWithoutOnlineApp() {
        ExpenseTransaction existing = existingTransaction();
        when(transactionMapper.selectOne(any())).thenReturn(existing);
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(ownedCategory());
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(ownedPaymentMethod());

        ExpenseTransaction updated = service.update(USER_ID, TRANSACTION_ID, new TransactionRequest(
                "INCOME",
                "  退款  ",
                new BigDecimal("88.00"),
                OCCURRED_AT.plusDays(1),
                "ONLINE",
                "   ",
                null,
                null,
                PAYMENT_METHOD_ID,
                CATEGORY_ID,
                "  退货  "));

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(updated.getUserId()).isEqualTo(USER_ID);
        assertThat(updated.getType()).isEqualTo("INCOME");
        assertThat(updated.getItemName()).isEqualTo("退款");
        assertThat(updated.getAmount()).isEqualByComparingTo("88.00");
        assertThat(updated.getOccurredAt()).isEqualTo(OCCURRED_AT.plusDays(1));
        assertThat(updated.getChannel()).isEqualTo("ONLINE");
        assertThat(updated.getOnlineApp()).isNull();
        assertThat(updated.getOfflinePlace()).isNull();
        assertThat(updated.getPaymentMethodId()).isEqualTo(PAYMENT_METHOD_ID);
        assertThat(updated.getPaymentMethodName()).isEqualTo("微信");
        assertThat(updated.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(updated.getNote()).isEqualTo("退货");
        verify(transactionMapper).updateById(existing);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_UPDATE", "TRANSACTION", TRANSACTION_ID, "USER");
    }

    @Test
    void updatePreservesOnlinePlatformIdAndSnapshotName() {
        ExpenseTransaction existing = existingTransaction();
        existing.setChannel("ONLINE");
        existing.setOnlinePlatformId(4001L);
        existing.setOnlineApp("美团");
        existing.setOfflinePlace(null);
        OnlinePlatform renamedPlatform = onlinePlatform(4001L, "美团外卖", 10, false);
        when(transactionMapper.selectOne(any())).thenReturn(existing);
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(ownedCategory());
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(ownedPaymentMethod());
        when(onlinePlatformService.requireOwned(USER_ID, 4001L)).thenReturn(renamedPlatform);

        ExpenseTransaction updated = service.update(USER_ID, TRANSACTION_ID, new TransactionRequest(
                "EXPENSE",
                "  午餐  ",
                new BigDecimal("20.00"),
                OCCURRED_AT.plusDays(1),
                "ONLINE",
                "美团",
                4001L,
                null,
                PAYMENT_METHOD_ID,
                CATEGORY_ID,
                "  改备注  "));

        assertThat(updated).isSameAs(existing);
        assertThat(updated.getOnlinePlatformId()).isEqualTo(4001L);
        assertThat(updated.getOnlineApp()).isEqualTo("美团");
        assertThat(updated.getNote()).isEqualTo("改备注");
        verify(transactionMapper).updateById(existing);
    }

    @Test
    void deleteRemovesOwnedRecord() {
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction());

        service.delete(USER_ID, TRANSACTION_ID);

        verify(transactionMapper).deleteById(TRANSACTION_ID);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_DELETE", "TRANSACTION", TRANSACTION_ID, "USER");
    }

    @Test
    void deleteImageWritesBusinessAuditLog() {
        service.deleteImage(USER_ID, TRANSACTION_ID, 99L);

        verify(transactionImageService).deleteImage(USER_ID, TRANSACTION_ID, 99L);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_IMAGE_DELETE", "TRANSACTION_IMAGE", 99L, "USER");
    }

    @Test
    void duplicateCheckIgnoresOnlinePlatformWhenImportOnlyProvidesSnapshotName() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ExpenseTransaction.class);
        when(transactionMapper.selectCount(any())).thenReturn(1L);

        boolean exists = service.existsSameTransaction(USER_ID, request(
                "EXPENSE",
                "ONLINE",
                "美团",
                null,
                "下午茶"));

        ArgumentCaptor<LambdaQueryWrapper<ExpenseTransaction>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(transactionMapper).selectCount(captor.capture());
        assertThat(exists).isTrue();
        assertThat(captor.getValue().getSqlSegment())
                .contains("online_app")
                .doesNotContain("online_platform_id");
    }

    @Test
    void getThrowsWhenRecordMissing() {
        when(transactionMapper.selectRecord(USER_ID, TRANSACTION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.get(USER_ID, TRANSACTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("记录不存在");
    }

    @Test
    void listUsesDateBoundsAndPagination() {
        TransactionResponse row = transactionResponse(
                1L,
                "EXPENSE",
                "奶茶",
                "12.50",
                OCCURRED_AT,
                "ONLINE",
                "美团",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                "下午茶");
        LocalDateTime startAt = START_DATE.atStartOfDay();
        LocalDateTime endAt = END_DATE.plusDays(1).atStartOfDay();
        when(transactionMapper.countRecords(USER_ID, "EXPENSE", startAt, endAt, "ONLINE", CATEGORY_ID, PAYMENT_METHOD_ID, "奶茶"))
                .thenReturn(23L);
        when(transactionMapper.selectRecords(USER_ID, "EXPENSE", startAt, endAt, "ONLINE", CATEGORY_ID, PAYMENT_METHOD_ID, "奶茶", 10, 10L))
                .thenReturn(List.of(row));

        PageResponse<TransactionResponse> page = service.list(
                USER_ID,
                "EXPENSE",
                START_DATE,
                END_DATE,
                "ONLINE",
                CATEGORY_ID,
                PAYMENT_METHOD_ID,
                "奶茶",
                2,
                10);

        assertThat(page.total()).isEqualTo(23L);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.totalPages()).isEqualTo(3L);
        assertThat(page.records()).containsExactly(row);
    }

    @Test
    void dailyCardsFillBalancesAndRecordPages() {
        TransactionResponse row = transactionResponse(
                2L,
                "EXPENSE",
                "咖啡",
                "18.00",
                DAY.atTime(9, 15),
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                null);
        TransactionDayCardResponse day = dayCard(DAY, null, new BigDecimal("80.00"), 2L);
        when(transactionMapper.countRecords(USER_ID, null, null, null, null, null, null, null)).thenReturn(2L);
        when(transactionMapper.countRecordDays(USER_ID, null, null, null, null, null, null, null)).thenReturn(1L);
        when(transactionMapper.selectDayCards(USER_ID, null, null, null, null, null, null, null, 30, 0L))
                .thenReturn(List.of(day));
        when(transactionMapper.selectRecords(USER_ID, null, DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay(), null, null, null, null, 5, 0L))
                .thenReturn(List.of(row));

        TransactionDayCardsResponse response = service.dailyCards(
                USER_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1,
                30,
                1,
                5);

        assertThat(response.totalRecords()).isEqualTo(2L);
        assertThat(response.totalDays()).isEqualTo(1L);
        assertThat(response.totalDayPages()).isEqualTo(1L);
        assertThat(response.dayPage()).isEqualTo(1);
        assertThat(response.daySize()).isEqualTo(30);
        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(response.days().get(0).getTotalIncome()).isEqualByComparingTo("80.00");
        assertThat(response.days().get(0).getBalance()).isEqualByComparingTo("80.00");
        assertThat(response.days().get(0).getRecords().total()).isEqualTo(2L);
        assertThat(response.days().get(0).getRecords().page()).isEqualTo(1);
        assertThat(response.days().get(0).getRecords().size()).isEqualTo(5);
        assertThat(response.days().get(0).getRecords().records()).containsExactly(row);
    }

    @Test
    void dailyOptionsFillBalances() {
        TransactionDayOptionResponse zero = dayOption(DAY, null, null, 0L);
        TransactionDayOptionResponse active = dayOption(DAY.plusDays(1), new BigDecimal("25.00"), new BigDecimal("10.00"), 2L);
        when(transactionMapper.selectDayOptions(USER_ID, "EXPENSE", null, null, null, null, null, null))
                .thenReturn(List.of(zero, active));

        List<TransactionDayOptionResponse> days = service.dailyOptions(
                USER_ID,
                "EXPENSE",
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).getBalance()).isEqualByComparingTo("0");
        assertThat(days.get(1).getTotalExpense()).isEqualByComparingTo("25.00");
        assertThat(days.get(1).getTotalIncome()).isEqualByComparingTo("10.00");
        assertThat(days.get(1).getBalance()).isEqualByComparingTo("-15.00");
    }

    @Test
    void recommendationsFilterByType() {
        TransactionResponse income = transactionResponse(
                31L,
                "INCOME",
                "交通报销",
                "236.80",
                LocalDateTime.now().minusDays(7),
                "ONLINE",
                null,
                null,
                PAYMENT_METHOD_ID,
                "银行卡",
                CATEGORY_ID,
                "报销",
                null);
        when(transactionMapper.selectRecords(
                eq(USER_ID),
                eq("INCOME"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(300),
                eq(0L)))
                .thenReturn(List.of(income));
        stubOwnedReferences();

        List<TransactionTemplateResponse> templates = service.recommendTemplates(USER_ID, "INCOME", 5);

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).type()).isEqualTo("INCOME");
        assertThat(templates.get(0).itemName()).isEqualTo("交通报销");
    }

    @Test
    void recommendationsSeparateOnlinePlatformCombinations() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionResponse meituan = transactionResponse(
                41L,
                "EXPENSE",
                "外卖",
                "38.00",
                now.minusDays(1),
                "ONLINE",
                null,
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        meituan.setOnlinePlatformId(4001L);
        TransactionResponse eleme = transactionResponse(
                42L,
                "EXPENSE",
                "外卖",
                "39.00",
                now.minusDays(1).minusMinutes(5),
                "ONLINE",
                null,
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        eleme.setOnlinePlatformId(4002L);
        when(transactionMapper.selectRecords(
                eq(USER_ID),
                eq("EXPENSE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(300),
                eq(0L)))
                .thenReturn(List.of(meituan, eleme));
        stubOwnedReferences();
        when(onlinePlatformService.requireOwned(USER_ID, 4001L)).thenReturn(onlinePlatform(4001L, "美团", 0, false));
        when(onlinePlatformService.requireOwned(USER_ID, 4002L)).thenReturn(onlinePlatform(4002L, "饿了么", 0, false));

        List<TransactionTemplateResponse> templates = service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).hasSize(2);
        assertThat(templates).extracting(TransactionTemplateResponse::onlinePlatformId)
                .containsExactly(4001L, 4002L);
    }

    @Test
    void recommendationsUseLatestAmountForRepeatedTemplate() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionResponse older = transactionResponse(
                51L,
                "EXPENSE",
                "早餐",
                "8.00",
                now.minusDays(20),
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        TransactionResponse latest = transactionResponse(
                52L,
                "EXPENSE",
                "早餐",
                "12.00",
                now.minusDays(2),
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        when(transactionMapper.selectRecords(
                eq(USER_ID),
                eq("EXPENSE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(300),
                eq(0L)))
                .thenReturn(List.of(older, latest));
        stubOwnedReferences();

        List<TransactionTemplateResponse> templates = service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).amount()).isEqualByComparingTo("12.00");
        assertThat(templates.get(0).reason()).contains("金额参考最近记录");
    }

    @Test
    void recommendationsPreferRecentTemplateOverOldFrequentTemplate() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        TransactionResponse recent = transactionResponse(
                61L,
                "EXPENSE",
                "咖啡",
                "18.00",
                now.minusDays(2),
                "OFFLINE",
                null,
                "公司楼下",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                null);
        List<TransactionResponse> rows = new java.util.ArrayList<>();
        rows.add(recent);
        for (int index = 0; index < 12; index++) {
            rows.add(transactionResponse(
                    70L + index,
                    "EXPENSE",
                    "旧午餐",
                    "30.00",
                    now.minusDays(120 + index),
                    "OFFLINE",
                    null,
                    "老食堂",
                    PAYMENT_METHOD_ID,
                    "微信",
                    CATEGORY_ID,
                    "餐饮",
                    null));
        }
        when(transactionMapper.selectRecords(
                eq(USER_ID),
                eq("EXPENSE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(300),
                eq(0L)))
                .thenReturn(rows);
        stubOwnedReferences();

        List<TransactionTemplateResponse> templates = service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).isNotEmpty();
        assertThat(templates.get(0).itemName()).isEqualTo("咖啡");
    }

    @Test
    void contextRecommendationsPreferExactItemNameMatch() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 20, 15, 0);
        TransactionResponse exact = transactionResponse(
                11L,
                "EXPENSE",
                "奶茶",
                "16.00",
                contextAt.minusDays(20).withHour(10),
                "ONLINE",
                "美团",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                null);
        TransactionResponse categoryOnly = transactionResponse(
                12L,
                "EXPENSE",
                "下午茶",
                "22.00",
                contextAt.minusDays(1),
                "OFFLINE",
                null,
                "公司楼下",
                3002L,
                "支付宝",
                2002L,
                "奶茶",
                null);
        when(transactionMapper.selectRecords(USER_ID, "EXPENSE", contextAt.minusDays(180), contextAt, null, null, null, null, 300, 0L))
                .thenReturn(List.of(categoryOnly, exact));
        stubOwnedReferences(CATEGORY_ID, PAYMENT_METHOD_ID);
        stubOwnedReferences(2002L, 3002L);

        List<TransactionTemplateResponse> templates = service.recommendContextTemplates(
                USER_ID, "奶茶", "EXPENSE", null, contextAt, 3);

        assertThat(templates).hasSize(2);
        assertThat(templates.get(0).itemName()).isEqualTo("奶茶");
        assertThat(templates.get(1).itemName()).isEqualTo("下午茶");
    }

    @Test
    void contextRecommendationsUseLatestAmountForRepeatedTemplate() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 20, 9, 30);
        TransactionResponse latest = transactionResponse(
                21L,
                "EXPENSE",
                "早餐",
                "12.00",
                contextAt.minusDays(2),
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        TransactionResponse older = transactionResponse(
                20L,
                "EXPENSE",
                "早餐",
                "8.00",
                contextAt.minusDays(30),
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        when(transactionMapper.selectRecords(USER_ID, "EXPENSE", contextAt.minusDays(180), contextAt, "OFFLINE", null, null, null, 300, 0L))
                .thenReturn(List.of(latest, older));
        stubOwnedReferences();

        List<TransactionTemplateResponse> templates = service.recommendContextTemplates(
                USER_ID, "早餐", "EXPENSE", "OFFLINE", contextAt, 3);

        assertThat(templates).hasSize(1);
        assertThat(templates.get(0).amount()).isEqualByComparingTo("12.00");
        assertThat(templates.get(0).reason()).contains("历史出现 2 次", "金额参考最近记录");
    }

    @Test
    void contextRecommendationsSkipInactiveReferences() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 20, 12, 0);
        TransactionResponse inactive = transactionResponse(
                31L,
                "EXPENSE",
                "午餐",
                "35.00",
                contextAt.minusDays(1),
                "OFFLINE",
                null,
                "食堂",
                3999L,
                "已删支付",
                2999L,
                "已删分类",
                null);
        when(transactionMapper.selectRecords(USER_ID, "EXPENSE", contextAt.minusDays(180), contextAt, null, null, null, null, 300, 0L))
                .thenReturn(List.of(inactive));
        when(categoryService.requireOwned(USER_ID, 2999L)).thenThrow(new IllegalArgumentException("分类不存在"));

        List<TransactionTemplateResponse> templates = service.recommendContextTemplates(
                USER_ID, "午餐", "EXPENSE", null, contextAt, 3);

        assertThat(templates).isEmpty();
    }

    @Test
    void recommendationsSkipDeletedOnlinePlatformReferences() {
        LocalDateTime contextAt = LocalDateTime.now();
        TransactionResponse inactivePlatform = transactionResponse(
                32L,
                "EXPENSE",
                "外卖",
                "45.00",
                contextAt.minusDays(1),
                "ONLINE",
                "已删平台",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        inactivePlatform.setOnlinePlatformId(4999L);
        when(transactionMapper.selectRecords(
                eq(USER_ID),
                eq("EXPENSE"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(300),
                eq(0L)))
                .thenReturn(List.of(inactivePlatform));
        stubOwnedReferences();
        when(onlinePlatformService.requireOwned(USER_ID, 4999L)).thenThrow(new IllegalArgumentException("线上平台不存在"));

        List<TransactionTemplateResponse> templates = service.recommendTemplates(USER_ID, "EXPENSE", 5);

        assertThat(templates).isEmpty();
    }

    @Test
    void contextRecommendationsReturnEmptyForBlankOrWeakQuery() {
        LocalDateTime contextAt = LocalDateTime.of(2026, 5, 20, 12, 0);
        assertThat(service.recommendContextTemplates(USER_ID, "  ", "EXPENSE", null, contextAt, 3)).isEmpty();

        TransactionResponse weak = transactionResponse(
                41L,
                "EXPENSE",
                "午餐",
                "35.00",
                contextAt.minusDays(1),
                "OFFLINE",
                null,
                "食堂",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        when(transactionMapper.selectRecords(USER_ID, "EXPENSE", contextAt.minusDays(180), contextAt, null, null, null, null, 300, 0L))
                .thenReturn(List.of(weak));
        stubOwnedReferences();

        List<TransactionTemplateResponse> templates = service.recommendContextTemplates(
                USER_ID, "打车", "EXPENSE", null, contextAt, 3);

        assertThat(templates).isEmpty();
    }

    @Test
    void quickEntryRecommendationsPreferRecentAndPinnedOptions() {
        LocalDateTime contextAt = LocalDateTime.now().minusMinutes(5);
        Category pinnedCategory = ownedCategory();
        pinnedCategory.setId(2002L);
        pinnedCategory.setName("交通");
        pinnedCategory.setSortOrder(20);
        pinnedCategory.setPinned(true);
        Category recentCategory = ownedCategory();
        recentCategory.setName("餐饮");
        recentCategory.setSortOrder(10);
        PaymentMethod wechat = ownedPaymentMethod();
        wechat.setSortOrder(10);
        PaymentMethod cash = ownedPaymentMethod();
        cash.setId(3002L);
        cash.setName("现金");
        cash.setSortOrder(20);
        OnlinePlatform meituan = onlinePlatform(4001L, "美团", 10, false);
        OnlinePlatform taobao = onlinePlatform(4002L, "淘宝", 20, true);
        TransactionResponse row = transactionResponse(
                51L,
                "EXPENSE",
                null,
                "22.00",
                contextAt,
                "ONLINE",
                "美团",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "餐饮",
                null);
        row.setOnlinePlatformId(4001L);
        when(transactionMapper.selectRecords(eq(USER_ID), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull(), eq(500), eq(0L)))
                .thenReturn(List.of(row));
        when(transactionMapper.selectRecords(eq(USER_ID), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class), isNull(), isNull(), isNull(), isNull(), eq(300), eq(0L)))
                .thenReturn(List.of(row));
        when(categoryService.list(USER_ID, "EXPENSE")).thenReturn(List.of(recentCategory, pinnedCategory));
        when(paymentMethodService.list(USER_ID)).thenReturn(List.of(wechat, cash));
        when(onlinePlatformService.list(USER_ID)).thenReturn(List.of(meituan, taobao));
        when(onlinePlatformService.requireOwned(USER_ID, 4001L)).thenReturn(meituan);
        stubOwnedReferences();

        QuickEntryRecommendationsResponse response = service.recommendQuickEntry(USER_ID, "EXPENSE", 10);

        assertThat(response.categories()).extracting(Category::getName).containsExactly("交通", "餐饮");
        assertThat(response.paymentMethods()).extracting(PaymentMethod::getName).containsExactly("微信", "现金");
        assertThat(response.onlinePlatforms()).extracting(OnlinePlatform::getName).containsExactly("淘宝", "美团");
        assertThat(response.combinations()).hasSize(1);
    }

    private void stubOwnedReferences() {
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(ownedCategory());
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(ownedPaymentMethod());
    }

    private void stubOwnedReferences(Long categoryId, Long paymentMethodId) {
        Category category = ownedCategory();
        category.setId(categoryId);
        PaymentMethod paymentMethod = ownedPaymentMethod();
        paymentMethod.setId(paymentMethodId);
        when(categoryService.requireOwned(USER_ID, categoryId)).thenReturn(category);
        when(paymentMethodService.requireOwned(USER_ID, paymentMethodId)).thenReturn(paymentMethod);
    }

    private TransactionRequest request(String type, String channel, String onlineApp, String offlinePlace, String note) {
        return new TransactionRequest(
                type,
                "  午餐  ",
                new BigDecimal("12.50"),
                OCCURRED_AT,
                channel,
                onlineApp,
                null,
                offlinePlace,
                PAYMENT_METHOD_ID,
                CATEGORY_ID,
                note);
    }

    private Category ownedCategory() {
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("饮料");
        category.setType("EXPENSE");
        return category;
    }

    private PaymentMethod ownedPaymentMethod() {
        PaymentMethod paymentMethod = new PaymentMethod();
        paymentMethod.setId(PAYMENT_METHOD_ID);
        paymentMethod.setUserId(USER_ID);
        paymentMethod.setName("微信");
        return paymentMethod;
    }

    private OnlinePlatform onlinePlatform(Long id, String name, int sortOrder, boolean pinned) {
        OnlinePlatform platform = new OnlinePlatform();
        platform.setId(id);
        platform.setUserId(USER_ID);
        platform.setName(name);
        platform.setSortOrder(sortOrder);
        platform.setPinned(pinned);
        return platform;
    }

    private ExpenseTransaction existingTransaction() {
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(TRANSACTION_ID);
        transaction.setUserId(USER_ID);
        transaction.setType("EXPENSE");
        transaction.setItemName("旧记录");
        transaction.setAmount(new BigDecimal("1.00"));
        transaction.setOccurredAt(OCCURRED_AT);
        transaction.setChannel("OFFLINE");
        transaction.setOfflinePlace("旧地点");
        transaction.setPaymentMethodId(PAYMENT_METHOD_ID);
        transaction.setPaymentMethodName("现金");
        transaction.setCategoryId(CATEGORY_ID);
        transaction.setNote("旧备注");
        return transaction;
    }

    private TransactionResponse transactionResponse(
            Long id,
            String type,
            String itemName,
            String amount,
            LocalDateTime occurredAt,
            String channel,
            String onlineApp,
            String offlinePlace,
            Long paymentMethodId,
            String paymentMethodName,
            Long categoryId,
            String categoryName,
            String note
    ) {
        TransactionResponse response = new TransactionResponse();
        response.setId(id);
        response.setType(type);
        response.setItemName(itemName);
        response.setAmount(new BigDecimal(amount));
        response.setOccurredAt(occurredAt);
        response.setChannel(channel);
        response.setOnlineApp(onlineApp);
        response.setOfflinePlace(offlinePlace);
        response.setPaymentMethodId(paymentMethodId);
        response.setPaymentMethodName(paymentMethodName);
        response.setCategoryId(categoryId);
        response.setCategoryName(categoryName);
        response.setNote(note);
        return response;
    }

    private TransactionDayCardResponse dayCard(LocalDate date, BigDecimal totalExpense, BigDecimal totalIncome, long transactionCount) {
        TransactionDayCardResponse response = new TransactionDayCardResponse();
        response.setDate(date);
        response.setTotalExpense(totalExpense);
        response.setTotalIncome(totalIncome);
        response.setTransactionCount(transactionCount);
        return response;
    }

    private TransactionDayOptionResponse dayOption(LocalDate date, BigDecimal totalExpense, BigDecimal totalIncome, long transactionCount) {
        TransactionDayOptionResponse response = new TransactionDayOptionResponse();
        response.setDate(date);
        response.setTotalExpense(totalExpense);
        response.setTotalIncome(totalIncome);
        response.setTransactionCount(transactionCount);
        return response;
    }
}
