package com.example.expense.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.web.PageResponse;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private TransactionMapper transactionMapper;
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionMapper, categoryService, paymentMethodService);
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
    }

    @Test
    void deleteRemovesOwnedRecord() {
        when(transactionMapper.selectOne(any())).thenReturn(existingTransaction());

        service.delete(USER_ID, TRANSACTION_ID);

        verify(transactionMapper).deleteById(TRANSACTION_ID);
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

    private TransactionRequest request(String type, String channel, String onlineApp, String offlinePlace, String note) {
        return new TransactionRequest(
                type,
                "  午餐  ",
                new BigDecimal("12.50"),
                OCCURRED_AT,
                channel,
                onlineApp,
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
