package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.businessaudit.service.BusinessAuditLogService;
import com.example.expense.common.cache.CacheInvalidationService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import com.example.expense.transaction.dto.AiSceneAvailabilityResponse;
import com.example.expense.transaction.dto.AiSceneRecommendationRequest;
import com.example.expense.transaction.dto.AiSceneRecommendationResponse;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TrashClearResponse;
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
    private CacheInvalidationService cacheInvalidationService;
    @Mock
    private BusinessAuditLogService businessAuditLogService;
    @Mock
    private TransactionAiRecommendationService aiRecommendationService;
    private TransactionRecommendationService recommendationService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        recommendationService = new TransactionRecommendationService(
                transactionMapper,
                categoryService,
                paymentMethodService,
                onlinePlatformService,
                CLOCK
        );
        service = new TransactionService(
                transactionMapper,
                categoryService,
                paymentMethodService,
                onlinePlatformService,
                transactionImageService,
                recommendationService,
                aiRecommendationService,
                cacheInvalidationService,
                businessAuditLogService,
                CLOCK
        );
    }

    @Test
    void recommendAiSceneDelegatesAuthenticatedUserAndRequestUnchanged() {
        AiSceneRecommendationRequest request = new AiSceneRecommendationRequest("乐园", "EXPENSE");
        AiSceneRecommendationResponse expected = new AiSceneRecommendationResponse(
                "SUGGESTED", 12L, "娱乐", "OFFLINE", null, null, 0.91, "匹配");
        when(aiRecommendationService.recommend(USER_ID, request)).thenReturn(expected);

        AiSceneRecommendationResponse response = service.recommendAiScene(USER_ID, request);

        assertThat(response).isSameAs(expected);
        verify(aiRecommendationService).recommend(USER_ID, request);
    }

    @Test
    void aiSceneAvailabilityDelegatesWithoutUserContext() {
        AiSceneAvailabilityResponse expected = new AiSceneAvailabilityResponse(false);
        when(aiRecommendationService.availability()).thenReturn(expected);

        AiSceneAvailabilityResponse response = service.aiSceneAvailability();

        assertThat(response).isSameAs(expected);
        verify(aiRecommendationService).availability();
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
    void deleteMovesOwnedRecordToTrashAtClockTimeWithoutDeletingImages() {
        when(transactionMapper.moveToTrash(USER_ID, TRANSACTION_ID, LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone())))
                .thenReturn(1);

        service.delete(USER_ID, TRANSACTION_ID);

        verify(transactionMapper).moveToTrash(
                USER_ID, TRANSACTION_ID, LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone()));
        verifyNoInteractions(transactionImageService);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_TRASH", "TRANSACTION", TRANSACTION_ID, "USER");
        verifyEvicted(USER_ID);
    }

    @Test
    void restoreRequiresTrashedRecordValidatesReferencesAndKeepsImages() {
        ExpenseTransaction trashed = existingTransaction();
        trashed.setTrashedAt(LocalDateTime.of(2026, 5, 20, 8, 30));
        trashed.setOnlinePlatformId(4001L);
        when(transactionMapper.selectTrashedTransaction(USER_ID, TRANSACTION_ID)).thenReturn(trashed);
        when(transactionMapper.restoreFromTrash(USER_ID, TRANSACTION_ID)).thenReturn(1);
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(ownedCategory());
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(ownedPaymentMethod());
        when(onlinePlatformService.requireOwned(USER_ID, 4001L)).thenReturn(onlinePlatform(4001L, "美团", 1, false));
        when(transactionMapper.selectRecord(USER_ID, TRANSACTION_ID)).thenReturn(transactionResponse(
                TRANSACTION_ID, "EXPENSE", "午餐", "12.50", OCCURRED_AT, "OFFLINE", null, "公司",
                PAYMENT_METHOD_ID, "微信", CATEGORY_ID, "餐饮", null));

        TransactionResponse response = service.restore(USER_ID, TRANSACTION_ID);

        assertThat(response.getId()).isEqualTo(TRANSACTION_ID);
        verify(transactionMapper).restoreFromTrash(USER_ID, TRANSACTION_ID);
        verify(categoryService).requireOwned(USER_ID, CATEGORY_ID);
        verify(paymentMethodService).requireOwned(USER_ID, PAYMENT_METHOD_ID);
        verify(onlinePlatformService).requireOwned(USER_ID, 4001L);
        verify(transactionImageService, never()).softDeleteByTransaction(USER_ID, TRANSACTION_ID);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_RESTORE", "TRANSACTION", TRANSACTION_ID, "USER");
        verifyEvicted(USER_ID);
    }

    @Test
    void restoreRejectsActiveRecord() {
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction());

        assertThatThrownBy(() -> service.restore(USER_ID, TRANSACTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("记录不在回收站");

        verify(transactionImageService, never()).softDeleteByTransaction(USER_ID, TRANSACTION_ID);
    }

    @Test
    void permanentlyDeleteTrashedRecordDeletesImagesBeforeTransaction() {
        ExpenseTransaction trashed = existingTransaction();
        trashed.setTrashedAt(LocalDateTime.of(2026, 5, 20, 8, 30));
        when(transactionMapper.selectTrashedTransaction(USER_ID, TRANSACTION_ID)).thenReturn(trashed);
        when(transactionMapper.softDeleteTrashed(USER_ID, TRANSACTION_ID)).thenReturn(1);

        service.permanentlyDelete(USER_ID, TRANSACTION_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(transactionImageService, transactionMapper);
        order.verify(transactionImageService).softDeleteByTransaction(USER_ID, TRANSACTION_ID);
        order.verify(transactionMapper).softDeleteTrashed(USER_ID, TRANSACTION_ID);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_DELETE", "TRANSACTION", TRANSACTION_ID, "USER");
        verifyEvicted(USER_ID);
    }

    @Test
    void permanentlyDeleteRejectsActiveRecord() {
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction());

        assertThatThrownBy(() -> service.permanentlyDelete(USER_ID, TRANSACTION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("记录不在回收站");

        verify(transactionImageService, never()).softDeleteByTransaction(USER_ID, TRANSACTION_ID);
    }

    @Test
    void clearTrashDeletesOnlyLockedCurrentUserRowsAndReturnsActualCount() {
        when(transactionMapper.selectTrashedIdsForUpdate(USER_ID)).thenReturn(List.of(88L, 89L));
        when(transactionMapper.softDeleteTrashed(USER_ID, 88L)).thenReturn(1);
        when(transactionMapper.softDeleteTrashed(USER_ID, 89L)).thenReturn(1);

        TrashClearResponse response = service.clearTrash(USER_ID);

        assertThat(response.deletedCount()).isEqualTo(2);
        verify(transactionMapper).selectTrashedIdsForUpdate(USER_ID);
        verify(transactionImageService).softDeleteByTransaction(USER_ID, 88L);
        verify(transactionImageService).softDeleteByTransaction(USER_ID, 89L);
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_DELETE", "TRANSACTION", 88L, "USER");
        verify(businessAuditLogService).recordSuccess(USER_ID, "TRANSACTION_DELETE", "TRANSACTION", 89L, "USER");
        verifyEvicted(USER_ID);
    }

    @Test
    void clearTrashReturnsZeroForEmptyTrash() {
        when(transactionMapper.selectTrashedIdsForUpdate(USER_ID)).thenReturn(List.of());

        TrashClearResponse response = service.clearTrash(USER_ID);

        assertThat(response.deletedCount()).isZero();
        verifyNoInteractions(transactionImageService, businessAuditLogService, cacheInvalidationService);
    }

    @Test
    void deleteWithoutBusinessAuditDirectlyDeletesActiveRecordAndImages() {
        when(transactionMapper.softDeleteActive(USER_ID, TRANSACTION_ID)).thenReturn(1);

        service.deleteWithoutBusinessAudit(USER_ID, TRANSACTION_ID);

        verify(transactionImageService).softDeleteByTransaction(USER_ID, TRANSACTION_ID);
        verify(transactionMapper).softDeleteActive(USER_ID, TRANSACTION_ID);
        verifyNoInteractions(businessAuditLogService);
        verifyEvicted(USER_ID);
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

    private void stubOwnedReferences() {
        when(categoryService.requireOwned(USER_ID, CATEGORY_ID)).thenReturn(ownedCategory());
        when(paymentMethodService.requireOwned(USER_ID, PAYMENT_METHOD_ID)).thenReturn(ownedPaymentMethod());
    }

    private void verifyEvicted(Long userId) {
        verify(cacheInvalidationService).evictStatisticsAfterCommit(userId);
        verify(cacheInvalidationService).evictRecommendationsAfterCommit(userId);
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
