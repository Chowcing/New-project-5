package com.example.expense.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.expense.statistics.dto.BudgetUsageSummary;
import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.ChannelSummary;
import com.example.expense.statistics.dto.DailySummary;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.dto.MonthlyTrendSummary;
import com.example.expense.statistics.dto.PaymentMethodSummary;
import com.example.expense.statistics.mapper.StatisticsMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
        MonthlyTotals previousTotals = totals("10.00", "80.00", 2L, 1L, 1L);
        DailySummary activeDay = dailySummary("2026-04-07", "12.50", "100.00", 2L);
        CategorySummary expenseCategory = categorySummary(10L, "餐饮", "12.50", 1L);
        CategorySummary incomeCategory = categorySummary(20L, "工资", "100.00", 1L);
        ChannelSummary channel = channelSummary("ONLINE", "12.50", 1L);
        PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "微信", "12.50", 1L);
        BudgetUsageSummary monthlyBudget = budgetUsage(null, "整月总预算", "100.00", "12.50", 1L);
        BudgetUsageSummary categoryBudget = budgetUsage(10L, "餐饮", "10.00", "12.50", 1L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals, previousTotals);
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
        when(statisticsMapper.selectBudgetUsage(eq(1001L), eq("2026-04"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(monthlyBudget, categoryBudget));

        var response = service.monthly(1001L, month);

        assertThat(response.month()).isEqualTo("2026-04");
        assertThat(response.balance()).isEqualByComparingTo("87.50");
        assertThat(response.transactionCount()).isEqualTo(2L);
        assertThat(response.expenseCount()).isEqualTo(1L);
        assertThat(response.incomeCount()).isEqualTo(1L);
        assertThat(response.insight().previousPeriod()).isEqualTo("2026-03");
        assertThat(response.insight().expenseChangeAmount()).isEqualByComparingTo("2.50");
        assertThat(response.insight().expenseChangePercent()).isEqualByComparingTo("25.00");
        assertThat(response.insight().averageDailyExpense()).isEqualByComparingTo("0.42");
        assertThat(response.insight().averageExpensePerTransaction()).isEqualByComparingTo("12.50");
        assertThat(response.insight().peakExpense().period()).isEqualTo("2026-04-07");
        assertThat(response.monthlyBudget().getBudgetAmount()).isEqualByComparingTo("100.00");
        assertThat(response.monthlyBudget().getUsagePercent()).isEqualByComparingTo("12.50");
        assertThat(response.categoryBudgetUsages()).hasSize(1);
        assertThat(response.categoryBudgetUsages().get(0).getRemainingAmount()).isEqualByComparingTo("-2.50");
        assertThat(response.categoryBudgetUsages().get(0).getOverBudget()).isTrue();
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
        MonthlyTotals previousTotals = totals("0.00", "0.00", 0L, 0L, 0L);
        MonthlyTrendSummary activeMonth = monthlyTrendSummary("2026-05", "1200.00", "5000.00", 20L);
        CategorySummary expenseCategory = categorySummary(10L, "交通", "1200.00", 15L);
        CategorySummary incomeCategory = categorySummary(20L, "工资", "5000.00", 5L);
        ChannelSummary channel = channelSummary("OFFLINE", "1200.00", 15L);
        PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "现金", "1200.00", 15L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals, previousTotals);
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
        assertThat(response.insight().previousPeriod()).isEqualTo("2025");
        assertThat(response.insight().expenseChangeAmount()).isEqualByComparingTo("1200.00");
        assertThat(response.insight().expenseChangePercent()).isNull();
        assertThat(response.insight().incomeChangePercent()).isNull();
        assertThat(response.insight().averageDailyExpense()).isEqualByComparingTo("3.29");
        assertThat(response.insight().averageExpensePerTransaction()).isEqualByComparingTo("80.00");
        assertThat(response.insight().peakExpense().period()).isEqualTo("2026-05");
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

    @Test
    void monthlyInsightUsesZeroPercentWhenBothPeriodsAreZeroAndNoBudget() {
        StatisticsService service = new StatisticsService(statisticsMapper);
        YearMonth month = YearMonth.of(2026, 6);
        MonthlyTotals totals = totals("0.00", "0.00", 0L, 0L, 0L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals, totals);
        when(statisticsMapper.selectBudgetUsage(eq(1001L), eq("2026-06"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        var response = service.monthly(1001L, month);

        assertThat(response.insight().expenseChangePercent()).isEqualByComparingTo("0.00");
        assertThat(response.insight().incomeChangePercent()).isEqualByComparingTo("0.00");
        assertThat(response.insight().peakExpense()).isNull();
        assertThat(response.monthlyBudget()).isNull();
        assertThat(response.categoryBudgetUsages()).isEmpty();
    }

    @Test
    void weeklyReturnsNaturalWeekStatisticsAndFilledDailyTrend() {
        StatisticsService service = new StatisticsService(statisticsMapper);
        LocalDate weekStart = LocalDate.of(2026, 6, 15);
        MonthlyTotals totals = totals("70.00", "200.00", 4L, 3L, 1L);
        MonthlyTotals previousTotals = totals("40.00", "150.00", 3L, 2L, 1L);
        DailySummary monday = dailySummary("2026-06-15", "10.00", "0.00", 1L);
        DailySummary wednesday = dailySummary("2026-06-17", "60.00", "200.00", 3L);
        CategorySummary expenseCategory = categorySummary(10L, "餐饮", "70.00", 3L);
        CategorySummary incomeCategory = categorySummary(20L, "工资", "200.00", 1L);
        ChannelSummary channel = channelSummary("ONLINE", "70.00", 3L);
        PaymentMethodSummary paymentMethod = paymentMethodSummary(30L, "微信", "70.00", 3L);

        when(statisticsMapper.selectMonthlyTotals(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(totals, previousTotals);
        when(statisticsMapper.selectDailySummary(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(monday, wednesday));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("EXPENSE"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(expenseCategory));
        when(statisticsMapper.selectCategorySummary(eq(1001L), eq("INCOME"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(incomeCategory));
        when(statisticsMapper.selectExpenseByChannel(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(channel));
        when(statisticsMapper.selectExpenseByPaymentMethod(eq(1001L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(paymentMethod));

        var response = service.weekly(1001L, weekStart);

        assertThat(response.weekStart()).isEqualTo("2026-06-15");
        assertThat(response.weekEnd()).isEqualTo("2026-06-21");
        assertThat(response.balance()).isEqualByComparingTo("130.00");
        assertThat(response.transactionCount()).isEqualTo(4L);
        assertThat(response.expenseCount()).isEqualTo(3L);
        assertThat(response.incomeCount()).isEqualTo(1L);
        assertThat(response.insight().currentPeriod()).isEqualTo("2026-06-15");
        assertThat(response.insight().previousPeriod()).isEqualTo("2026-06-08");
        assertThat(response.insight().expenseChangeAmount()).isEqualByComparingTo("30.00");
        assertThat(response.insight().expenseChangePercent()).isEqualByComparingTo("75.00");
        assertThat(response.insight().averageDailyExpense()).isEqualByComparingTo("10.00");
        assertThat(response.insight().averageExpensePerTransaction()).isEqualByComparingTo("23.33");
        assertThat(response.insight().peakExpense().period()).isEqualTo("2026-06-17");
        assertThat(response.dailyTrend()).hasSize(7);
        assertThat(response.dailyTrend().get(0).getDate()).isEqualTo("2026-06-15");
        assertThat(response.dailyTrend().get(1).getDate()).isEqualTo("2026-06-16");
        assertThat(response.dailyTrend().get(1).getTotalExpense()).isEqualByComparingTo("0");
        assertThat(response.dailyTrend().get(2).getDate()).isEqualTo("2026-06-17");
        assertThat(response.dailyTrend().get(2).getBalance()).isEqualByComparingTo("140.00");
        assertThat(response.expenseByCategory()).containsExactly(expenseCategory);
        assertThat(response.incomeByCategory()).containsExactly(incomeCategory);
        assertThat(response.expenseByChannel()).containsExactly(channel);
        assertThat(response.expenseByPaymentMethod()).containsExactly(paymentMethod);
    }

    @Test
    void weeklyRejectsNonMondayWeekStart() {
        StatisticsService service = new StatisticsService(statisticsMapper);

        assertThatThrownBy(() -> service.weekly(1001L, LocalDate.of(2026, 6, 16)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("周度统计起始日期必须是周一");
    }

    private MonthlyTotals totals(String totalExpense, String totalIncome, Long transactionCount, Long expenseCount, Long incomeCount) {
        MonthlyTotals totals = new MonthlyTotals();
        totals.setTotalExpense(new BigDecimal(totalExpense));
        totals.setTotalIncome(new BigDecimal(totalIncome));
        totals.setTransactionCount(transactionCount);
        totals.setExpenseCount(expenseCount);
        totals.setIncomeCount(incomeCount);
        return totals;
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

    private BudgetUsageSummary budgetUsage(Long categoryId, String categoryName, String budgetAmount, String usedAmount, Long transactionCount) {
        BudgetUsageSummary summary = new BudgetUsageSummary();
        summary.setCategoryId(categoryId);
        summary.setCategoryName(categoryName);
        summary.setBudgetAmount(new BigDecimal(budgetAmount));
        summary.setUsedAmount(new BigDecimal(usedAmount));
        summary.setTransactionCount(transactionCount);
        return summary;
    }
}
