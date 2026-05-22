package com.example.expense.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expense.common.security.UserPrincipal;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionDayCardResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {
    private static final Long USER_ID = 1001L;
    private static final Long CATEGORY_ID = 2001L;
    private static final Long PAYMENT_METHOD_ID = 3001L;
    private static final Long TRANSACTION_ID = 88L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 5, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 5, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 5, 14);
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 5, 14, 10, 30);

    private MockMvc mockMvc;
    @Mock
    private TransactionService transactionService;
    private LocalValidatorFactoryBean validator;
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;
    private ApplicationConversionService conversionService;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        jacksonMessageConverter = new MappingJackson2HttpMessageConverter(
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .findAndAddModules()
                        .build());
        conversionService = new ApplicationConversionService();
        mockMvc = MockMvcBuilders.standaloneSetup(new TransactionController(transactionService))
                .setValidator(validator)
                .setConversionService(conversionService)
                .setMessageConverters(jacksonMessageConverter)
                .build();
        SecurityContextHolder.clearContext();
        UserPrincipal principal = new UserPrincipal(USER_ID, "demo", false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listDelegatesUserIdAndReturnsPagePayload() throws Exception {
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
        when(transactionService.list(
                USER_ID,
                "EXPENSE",
                START_DATE,
                END_DATE,
                "ONLINE",
                CATEGORY_ID,
                PAYMENT_METHOD_ID,
                "奶茶",
                2,
                10)).thenReturn(PageResponse.of(List.of(row), 23L, 2, 10));

        mockMvc.perform(get("/api/v1/transactions")
                        .param("type", "EXPENSE")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-05-31")
                        .param("channel", "ONLINE")
                        .param("categoryId", String.valueOf(CATEGORY_ID))
                        .param("paymentMethodId", String.valueOf(PAYMENT_METHOD_ID))
                        .param("keyword", "奶茶")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.total").value(23))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.records[0].itemName").value("奶茶"));

        verify(transactionService).list(
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
    }

    @Test
    void getDelegatesPathIdAndReturnsRecord() throws Exception {
        TransactionResponse response = transactionResponse(
                TRANSACTION_ID,
                "EXPENSE",
                "咖啡",
                "18.00",
                OCCURRED_AT,
                "OFFLINE",
                null,
                "便利店",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                null);
        when(transactionService.get(USER_ID, TRANSACTION_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/{id}", TRANSACTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(TRANSACTION_ID.intValue()))
                .andExpect(jsonPath("$.data.itemName").value("咖啡"))
                .andExpect(jsonPath("$.data.offlinePlace").value("便利店"));

        verify(transactionService).get(USER_ID, TRANSACTION_ID);
    }

    @Test
    void createBindsRequestBodyAndReturnsSavedRecord() throws Exception {
        ExpenseTransaction saved = expenseTransaction(
                TRANSACTION_ID,
                "EXPENSE",
                "午餐",
                new BigDecimal("12.50"),
                OCCURRED_AT,
                "OFFLINE",
                null,
                "星巴克",
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                "下午茶");
        TransactionRequest request = new TransactionRequest(
                "EXPENSE",
                "午餐",
                new BigDecimal("12.50"),
                OCCURRED_AT,
                "OFFLINE",
                null,
                "星巴克",
                PAYMENT_METHOD_ID,
                CATEGORY_ID,
                "下午茶");
        when(transactionService.create(USER_ID, request)).thenReturn(saved);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "EXPENSE",
                                  "itemName": "午餐",
                                  "amount": 12.50,
                                  "occurredAt": "2026-05-14T10:30:00",
                                  "channel": "OFFLINE",
                                  "onlineApp": null,
                                  "offlinePlace": "星巴克",
                                  "paymentMethodId": 3001,
                                  "categoryId": 2001,
                                  "note": "下午茶"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("记录已保存"))
                .andExpect(jsonPath("$.data.id").value(TRANSACTION_ID.intValue()))
                .andExpect(jsonPath("$.data.paymentMethodName").value("微信"))
                .andExpect(jsonPath("$.data.offlinePlace").value("星巴克"));

        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).create(userCaptor.capture(), captor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void createRejectsInvalidBodyBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 12.50,
                                  "occurredAt": "2026-05-14T10:30:00",
                                  "channel": "OFFLINE",
                                  "offlinePlace": "星巴克",
                                  "paymentMethodId": 3001,
                                  "categoryId": 2001
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void updateBindsPathAndBodyAndReturnsUpdatedRecord() throws Exception {
        ExpenseTransaction updated = expenseTransaction(
                TRANSACTION_ID,
                "INCOME",
                "退款",
                new BigDecimal("88.00"),
                OCCURRED_AT.plusDays(1),
                "ONLINE",
                "支付宝",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "工资",
                "退货");
        TransactionRequest request = new TransactionRequest(
                "INCOME",
                "退款",
                new BigDecimal("88.00"),
                OCCURRED_AT.plusDays(1),
                "ONLINE",
                "支付宝",
                null,
                PAYMENT_METHOD_ID,
                CATEGORY_ID,
                "退货");
        when(transactionService.update(USER_ID, TRANSACTION_ID, request)).thenReturn(updated);

        mockMvc.perform(put("/api/v1/transactions/{id}", TRANSACTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCOME",
                                  "itemName": "退款",
                                  "amount": 88.00,
                                  "occurredAt": "2026-05-15T10:30:00",
                                  "channel": "ONLINE",
                                  "onlineApp": "支付宝",
                                  "offlinePlace": null,
                                  "paymentMethodId": 3001,
                                  "categoryId": 2001,
                                  "note": "退货"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("记录已更新"))
                .andExpect(jsonPath("$.data.type").value("INCOME"))
                .andExpect(jsonPath("$.data.onlineApp").value("支付宝"));

        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TransactionRequest> captor = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).update(userCaptor.capture(), idCaptor.capture(), captor.capture());
        assertThat(userCaptor.getValue()).isEqualTo(USER_ID);
        assertThat(idCaptor.getValue()).isEqualTo(TRANSACTION_ID);
        assertThat(captor.getValue()).isEqualTo(request);
    }

    @Test
    void deleteDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/v1/transactions/{id}", TRANSACTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("记录已删除"));

        verify(transactionService).delete(USER_ID, TRANSACTION_ID);
    }

    @Test
    void dailyCardsDelegatesAndReturnsNestedPages() throws Exception {
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
        TransactionDayCardResponse day = dayCard(DAY, new BigDecimal("0"), new BigDecimal("80.00"), 2L);
        day.setBalance(new BigDecimal("80.00"));
        day.setRecords(PageResponse.of(List.of(row), 2L, 1, 5));
        TransactionDayCardsResponse response = TransactionDayCardsResponse.of(List.of(day), 1L, 2L, 1, 30);
        when(transactionService.dailyCards(
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
                5)).thenReturn(response);

        mockMvc.perform(get("/api/v1/transactions/daily-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDays").value(1))
                .andExpect(jsonPath("$.data.totalRecords").value(2))
                .andExpect(jsonPath("$.data.days[0].balance").value(80.0))
                .andExpect(jsonPath("$.data.days[0].records.records[0].itemName").value("咖啡"));

        verify(transactionService).dailyCards(USER_ID, null, null, null, null, null, null, null, 1, 30, 1, 5);
    }

    @Test
    void dailyOptionsDelegatesAndReturnsList() throws Exception {
        TransactionDayOptionResponse active = dayOption(DAY, new BigDecimal("25.00"), new BigDecimal("10.00"), 2L);
        active.setBalance(new BigDecimal("-15.00"));
        when(transactionService.dailyOptions(USER_ID, "EXPENSE", null, null, null, null, null, null))
                .thenReturn(List.of(active));

        mockMvc.perform(get("/api/v1/transactions/daily-options")
                        .param("type", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].balance").value(-15.0))
                .andExpect(jsonPath("$.data[0].transactionCount").value(2));

        verify(transactionService).dailyOptions(USER_ID, "EXPENSE", null, null, null, null, null, null);
    }

    @Test
    void recommendationsDelegatesAndReturnsTemplates() throws Exception {
        TransactionTemplateResponse template = new TransactionTemplateResponse(
                "EXPENSE",
                "奶茶",
                new BigDecimal("12.50"),
                "ONLINE",
                "美团",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                "下午茶",
                "历史记录模板",
                88.5);
        when(transactionService.recommendTemplates(USER_ID, null, 5)).thenReturn(List.of(template));

        mockMvc.perform(get("/api/v1/transactions/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemName").value("奶茶"))
                .andExpect(jsonPath("$.data[0].score").value(88.5));

        verify(transactionService).recommendTemplates(USER_ID, null, 5);
    }

    @Test
    void recommendationsDelegatesTypeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/recommendations")
                        .param("type", "INCOME")
                        .param("limit", "3"))
                .andExpect(status().isOk());

        verify(transactionService).recommendTemplates(USER_ID, "INCOME", 3);
    }

    @Test
    void contextRecommendationsDelegatesAndReturnsTemplates() throws Exception {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 20, 15, 30);
        TransactionTemplateResponse template = new TransactionTemplateResponse(
                "EXPENSE",
                "奶茶",
                new BigDecimal("12.50"),
                "ONLINE",
                "美团",
                null,
                PAYMENT_METHOD_ID,
                "微信",
                CATEGORY_ID,
                "饮料",
                "下午茶",
                "历史出现 2 次",
                168.5);
        when(transactionService.recommendContextTemplates(USER_ID, "奶茶", "EXPENSE", "ONLINE", occurredAt, 3))
                .thenReturn(List.of(template));

        mockMvc.perform(get("/api/v1/transactions/recommendations/context")
                        .param("itemName", "奶茶")
                        .param("type", "EXPENSE")
                        .param("channel", "ONLINE")
                        .param("occurredAt", "2026-05-20T15:30:00")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].itemName").value("奶茶"))
                .andExpect(jsonPath("$.data[0].score").value(168.5));

        verify(transactionService).recommendContextTemplates(USER_ID, "奶茶", "EXPENSE", "ONLINE", occurredAt, 3);
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

    private ExpenseTransaction expenseTransaction(
            Long id,
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
            String categoryName,
            String note
    ) {
        ExpenseTransaction transaction = new ExpenseTransaction();
        transaction.setId(id);
        transaction.setUserId(USER_ID);
        transaction.setType(type);
        transaction.setItemName(itemName);
        transaction.setAmount(amount);
        transaction.setOccurredAt(occurredAt);
        transaction.setChannel(channel);
        transaction.setOnlineApp(onlineApp);
        transaction.setOfflinePlace(offlinePlace);
        transaction.setPaymentMethodId(paymentMethodId);
        transaction.setPaymentMethodName(paymentMethodName);
        transaction.setCategoryId(categoryId);
        transaction.setNote(note);
        return transaction;
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
