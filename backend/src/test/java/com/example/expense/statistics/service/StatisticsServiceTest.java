package com.example.expense.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.ChannelSummary;
import com.example.expense.statistics.dto.DailySummary;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.dto.MonthlyTrendSummary;
import com.example.expense.statistics.dto.PaymentMethodSummary;
import com.example.expense.statistics.mapper.StatisticsMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock
    private StatisticsMapper statisticsMapper;

    @Test
    void monthlyReturnsExtendedStatisticsAndFilledDailyTrend() {
        StatisticsService service = new StatisticsService(statisticsMapper);
        YearMonth month = YearMonth.of(2026, 4);
        MonthlyTotals totals = new MonthlyTotals();
        totals.setTotalExpense(new BigDecimal("12.50"));
        totals.setTotalIncome(new BigDecimal("100.00"));
        totals.setTransactionCount(2L);
        totals.setExpenseCount(1L);
        totals.setIncomeCount(1L);
        DailySummary activeDay = dailySummary("2026-04-07", "12.50", "100.00", 2L);
        CategorySummary expenseCategory = categorySummary(10L, "餐饮", "12.50", 1L);
        CategorySummary incomeCategory = categorySummary(20L, "工资", "100.00", 1L);
        ChannelSummary channel = channelSummary("ONLINE", "12.50", 1L);
        PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "微信", "12.50", 1L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals);
        when(statisticsMapper.selectDailySummary(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(activeDay));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(expenseCategory));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("INCOME"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(incomeCategory));
        when(statisticsMapper.selectExpenseByChannel(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(channel));
        when(statisticsMapper.selectExpenseByPaymentMethod(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentMethod));

        var response = service.monthly(1001L, month);

        assertThat(response.month()).isEqualTo("2026-04");
        assertThat(response.balance()).isEqualByComparingTo("87.50");
        assertThat(response.transactionCount()).isEqualTo(2L);
        assertThat(response.expenseCount()).isEqualTo(1L);
        assertThat(response.incomeCount()).isEqualTo(1L);
        assertThat(response.dailyTrend()).hasSize(30);
        assertThat(response.dailyTrend().get(0).getDate()).isEqualTo("2026-04-01");
        assertThat(response.dailyTrend().get(0).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(response.dailyTrend().get(6).getDate()).isEqualTo("2026-04-07");
        assertThat(response.dailyTrend().get(6).getBalance()).isEqualByComparingTo("87.50");
        assertThat(response.expenseByCategory()).containsExactly(expenseCategory);
        assertThat(response.incomeByCategory()).containsExactly(incomeCategory);
        assertThat(response.expenseByChannel()).containsExactly(channel);
        assertThat(response.expenseByPaymentMethod()).containsExactly(paymentMethod);
    }

    @Test
    void yearlyReturnsExtendedStatisticsAndFilledMonthlyTrend() {
        StatisticsService service = new StatisticsService(statisticsMapper);
        Year year = Year.of(2026);
        MonthlyTotals totals = new MonthlyTotals();
        totals.setTotalExpense(new BigDecimal("1200.00"));
        totals.setTotalIncome(new BigDecimal("5000.00"));
        totals.setTransactionCount(20L);
        totals.setExpenseCount(15L);
        totals.setIncomeCount(5L);
        MonthlyTrendSummary activeMonth = monthlyTrendSummary("2026-05", "1200.00", "5000.00", 20L);
        CategorySummary expenseCategory = categorySummary(10L, "交通", "1200.00", 15L);
        CategorySummary incomeCategory = categorySummary(20L, "工资", "5000.00", 5L);
        ChannelSummary channel = channelSummary("OFFLINE", "1200.00", 15L);
        PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "现金", "1200.00", 15L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals);
        when(statisticsMapper.selectMonthlyTrend(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(activeMonth));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(expenseCategory));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("INCOME"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(incomeCategory));
        when(statisticsMapper.selectExpenseByChannel(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(channel));
        when(statisticsMapper.selectExpenseByPaymentMethod(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentMethod));

        var response = service.yearly(1001L, year);

        assertThat(response.year()).isEqualTo("2026");
        assertThat(response.balance()).isEqualByComparingTo("3800.00");
        assertThat(response.transactionCount()).isEqualTo(20L);
        assertThat(response.expenseCount()).isEqualTo(15L);
        assertThat(response.incomeCount()).isEqualTo(5L);
        assertThat(response.monthlyTrend()).hasSize(12);
        assertThat(response.monthlyTrend().get(0).getMonth()).isEqualTo("2026-01");
        assertThat(response.monthlyTrend().get(0).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(response.monthlyTrend().get(4).getMonth()).isEqualTo("2026-05");
        assertThat(response.monthlyTrend().get(4).getBalance()).isEqualByComparingTo("3800.00");
        assertThat(response.expenseByCategory()).containsExactly(expenseCategory);
        assertThat(response.incomeByCategory()).containsExactly(incomeCategory);
        assertThat(response.expenseByChannel()).containsExactly(channel);
        assertThat(response.expenseByPaymentMethod()).containsExactly(paymentMethod);
    }

    private DailySummary dailySummary(String date, String totalExpense, String totalIncome, Long transactionCount) {
        DailySummary summary = new DailySummary();
        summary.setDate(date);
        summary.setTotalExpense(new BigDecimal(totalExpense));
        summary.setTotalIncome(new BigDecimal(totalIncome));
        summary.setTransactionCount(transactionCount);
        return summary;
    }

    private CategorySummary categorySummary(Long categoryId, String categoryName, String amount, Long transactionCount) {
        CategorySummary summary = new CategorySummary();
        summary.setCategoryId(categoryId);
        summary.setCategoryName(categoryName);
        summary.setAmount(new BigDecimal(amount));
        summary.setTransactionCount(transactionCount);
        return summary;
    }

    private MonthlyTrendSummary monthlyTrendSummary(String month, String totalExpense, String totalIncome, Long transactionCount) {
        MonthlyTrendSummary summary = new MonthlyTrendSummary();
        summary.setMonth(month);
        summary.setTotalExpense(new BigDecimal(totalExpense));
        summary.setTotalIncome(new BigDecimal(totalIncome));
        summary.setTransactionCount(transactionCount);
        return summary;
    }

    private ChannelSummary channelSummary(String channel, String amount, Long transactionCount) {
        ChannelSummary summary = new ChannelSummary();
        summary.setChannel(channel);
        summary.setAmount(new BigDecimal(amount));
        summary.setTransactionCount(transactionCount);
        return summary;
    }

    private PaymentMethodSummary paymentMethodSummary(Long paymentMethodId, String paymentMethodName, String amount, Long transactionCount) {
        PaymentMethodSummary summary = new PaymentMethodSummary();
        summary.setPaymentMethodId(paymentMethodId);
        summary.setPaymentMethodName(paymentMethodName);
        summary.setAmount(new BigDecimal(amount));
        summary.setTransactionCount(transactionCount);
        return summary;
    }
}
