package com.example.expense.statistics.service;

import com.example.expense.statistics.dto.BudgetUsageSummary;
import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.ChannelSummary;
import com.example.expense.statistics.dto.DailySummary;
import com.example.expense.statistics.dto.MonthlyStatisticsResponse;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.dto.MonthlyTrendSummary;
import com.example.expense.statistics.dto.PeakExpenseSummary;
import com.example.expense.statistics.dto.PaymentMethodSummary;
import com.example.expense.statistics.dto.StatisticsInsight;
import com.example.expense.statistics.dto.YearlyStatisticsResponse;
import com.example.expense.statistics.mapper.StatisticsMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
    private final StatisticsMapper statisticsMapper;

    public StatisticsService(StatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    public MonthlyStatisticsResponse monthly(Long userId, YearMonth month) {
        var startAt = month.atDay(1).atStartOfDay();
        var endAt = month.plusMonths(1).atDay(1).atStartOfDay();
        YearMonth previousMonth = month.minusMonths(1);
        var previousStartAt = previousMonth.atDay(1).atStartOfDay();
        var previousEndAt = previousMonth.plusMonths(1).atDay(1).atStartOfDay();
        // 月度统计全部在数据库侧聚合，避免把整月明细拉回 JVM 后再计算。
        MonthlyTotals totals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, startAt, endAt));
        MonthlyTotals previousTotals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, previousStartAt, previousEndAt));
        BigDecimal totalExpense = nullToZero(totals.getTotalExpense());
        BigDecimal totalIncome = nullToZero(totals.getTotalIncome());
        List<DailySummary> dailyTrend = fillDailyTrend(month, statisticsMapper.selectDailySummary(userId, startAt, endAt));
        List<CategorySummary> expenseByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "EXPENSE", startAt, endAt));
        List<CategorySummary> incomeByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "INCOME", startAt, endAt));
        List<ChannelSummary> expenseByChannel = safeList(statisticsMapper.selectExpenseByChannel(userId, startAt, endAt));
        List<PaymentMethodSummary> expenseByPaymentMethod = safeList(statisticsMapper.selectExpenseByPaymentMethod(userId, startAt, endAt));
        List<BudgetUsageSummary> budgetUsages = normalizeBudgetUsages(statisticsMapper.selectBudgetUsage(userId, month.toString(), startAt, endAt));
        BudgetUsageSummary monthlyBudget = budgetUsages.stream()
                .filter(item -> item.getCategoryId() == null)
                .findFirst()
                .orElse(null);
        List<BudgetUsageSummary> categoryBudgetUsages = budgetUsages.stream()
                .filter(item -> item.getCategoryId() != null)
                .toList();
        return new MonthlyStatisticsResponse(
                month.toString(),
                totalExpense,
                totalIncome,
                totalIncome.subtract(totalExpense),
                nullToZero(totals.getTransactionCount()),
                nullToZero(totals.getExpenseCount()),
                nullToZero(totals.getIncomeCount()),
                buildMonthlyInsight(month, totals, previousTotals, dailyTrend),
                monthlyBudget,
                categoryBudgetUsages,
                dailyTrend,
                expenseByCategory,
                incomeByCategory,
                expenseByChannel,
                expenseByPaymentMethod
        );
    }

    public YearlyStatisticsResponse yearly(Long userId, Year year) {
        var startAt = year.atDay(1).atStartOfDay();
        var endAt = year.plusYears(1).atDay(1).atStartOfDay();
        Year previousYear = year.minusYears(1);
        var previousStartAt = previousYear.atDay(1).atStartOfDay();
        var previousEndAt = previousYear.plusYears(1).atDay(1).atStartOfDay();
        MonthlyTotals totals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, startAt, endAt));
        MonthlyTotals previousTotals = safeTotals(statisticsMapper.selectMonthlyTotals(userId, previousStartAt, previousEndAt));
        BigDecimal totalExpense = nullToZero(totals.getTotalExpense());
        BigDecimal totalIncome = nullToZero(totals.getTotalIncome());
        List<MonthlyTrendSummary> monthlyTrend = fillMonthlyTrend(year, statisticsMapper.selectMonthlyTrend(userId, startAt, endAt));
        List<CategorySummary> expenseByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "EXPENSE", startAt, endAt));
        List<CategorySummary> incomeByCategory = safeList(statisticsMapper.selectCategorySummary(userId, "INCOME", startAt, endAt));
        List<ChannelSummary> expenseByChannel = safeList(statisticsMapper.selectExpenseByChannel(userId, startAt, endAt));
        List<PaymentMethodSummary> expenseByPaymentMethod = safeList(statisticsMapper.selectExpenseByPaymentMethod(userId, startAt, endAt));
        return new YearlyStatisticsResponse(
                year.toString(),
                totalExpense,
                totalIncome,
                totalIncome.subtract(totalExpense),
                nullToZero(totals.getTransactionCount()),
                nullToZero(totals.getExpenseCount()),
                nullToZero(totals.getIncomeCount()),
                buildYearlyInsight(year, totals, previousTotals, monthlyTrend),
                monthlyTrend,
                expenseByCategory,
                incomeByCategory,
                expenseByChannel,
                expenseByPaymentMethod
        );
    }

    private BigDecimal nullToZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private long nullToZero(Long count) {
        return count == null ? 0L : count;
    }

    private MonthlyTotals safeTotals(MonthlyTotals totals) {
        return totals == null ? new MonthlyTotals() : totals;
    }

    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? Collections.emptyList() : rows;
    }

    private BigDecimal amountScale(BigDecimal amount) {
        return nullToZero(amount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal divideAmount(BigDecimal amount, long divisor) {
        if (divisor <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return nullToZero(amount).divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        BigDecimal currentAmount = nullToZero(current);
        BigDecimal previousAmount = nullToZero(previous);
        if (previousAmount.compareTo(BigDecimal.ZERO) == 0) {
            return currentAmount.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : null;
        }
        return currentAmount.subtract(previousAmount)
                .multiply(BigDecimal.valueOf(100))
                .divide(previousAmount, 2, RoundingMode.HALF_UP);
    }

    private StatisticsInsight buildMonthlyInsight(
            YearMonth month,
            MonthlyTotals currentTotals,
            MonthlyTotals previousTotals,
            List<DailySummary> dailyTrend
    ) {
        currentTotals = safeTotals(currentTotals);
        previousTotals = safeTotals(previousTotals);
        BigDecimal currentExpense = nullToZero(currentTotals.getTotalExpense());
        BigDecimal currentIncome = nullToZero(currentTotals.getTotalIncome());
        BigDecimal currentBalance = currentIncome.subtract(currentExpense);
        BigDecimal previousExpense = nullToZero(previousTotals.getTotalExpense());
        BigDecimal previousIncome = nullToZero(previousTotals.getTotalIncome());
        BigDecimal previousBalance = previousIncome.subtract(previousExpense);
        return new StatisticsInsight(
                month.toString(),
                month.minusMonths(1).toString(),
                previousExpense,
                previousIncome,
                previousBalance,
                amountScale(currentExpense.subtract(previousExpense)),
                percentChange(currentExpense, previousExpense),
                amountScale(currentIncome.subtract(previousIncome)),
                percentChange(currentIncome, previousIncome),
                amountScale(currentBalance.subtract(previousBalance)),
                percentChange(currentBalance, previousBalance),
                divideAmount(currentExpense, month.lengthOfMonth()),
                divideAmount(currentExpense, nullToZero(currentTotals.getExpenseCount())),
                peakDailyExpense(dailyTrend)
        );
    }

    private StatisticsInsight buildYearlyInsight(
            Year year,
            MonthlyTotals currentTotals,
            MonthlyTotals previousTotals,
            List<MonthlyTrendSummary> monthlyTrend
    ) {
        currentTotals = safeTotals(currentTotals);
        previousTotals = safeTotals(previousTotals);
        BigDecimal currentExpense = nullToZero(currentTotals.getTotalExpense());
        BigDecimal currentIncome = nullToZero(currentTotals.getTotalIncome());
        BigDecimal currentBalance = currentIncome.subtract(currentExpense);
        BigDecimal previousExpense = nullToZero(previousTotals.getTotalExpense());
        BigDecimal previousIncome = nullToZero(previousTotals.getTotalIncome());
        BigDecimal previousBalance = previousIncome.subtract(previousExpense);
        return new StatisticsInsight(
                year.toString(),
                year.minusYears(1).toString(),
                previousExpense,
                previousIncome,
                previousBalance,
                amountScale(currentExpense.subtract(previousExpense)),
                percentChange(currentExpense, previousExpense),
                amountScale(currentIncome.subtract(previousIncome)),
                percentChange(currentIncome, previousIncome),
                amountScale(currentBalance.subtract(previousBalance)),
                percentChange(currentBalance, previousBalance),
                divideAmount(currentExpense, year.length()),
                divideAmount(currentExpense, nullToZero(currentTotals.getExpenseCount())),
                peakMonthlyExpense(monthlyTrend)
        );
    }

    private PeakExpenseSummary peakDailyExpense(List<DailySummary> dailyTrend) {
        return safeList(dailyTrend).stream()
                .filter(item -> nullToZero(item.getTotalExpense()).compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(item -> nullToZero(item.getTotalExpense())))
                .map(item -> new PeakExpenseSummary(
                        item.getDate(),
                        item.getDate().substring(5),
                        item.getTotalExpense(),
                        nullToZero(item.getTransactionCount())
                ))
                .orElse(null);
    }

    private PeakExpenseSummary peakMonthlyExpense(List<MonthlyTrendSummary> monthlyTrend) {
        return safeList(monthlyTrend).stream()
                .filter(item -> nullToZero(item.getTotalExpense()).compareTo(BigDecimal.ZERO) > 0)
                .max(Comparator.comparing(item -> nullToZero(item.getTotalExpense())))
                .map(item -> new PeakExpenseSummary(
                        item.getMonth(),
                        item.getMonth().substring(5) + "月",
                        item.getTotalExpense(),
                        nullToZero(item.getTransactionCount())
                ))
                .orElse(null);
    }

    private List<BudgetUsageSummary> normalizeBudgetUsages(List<BudgetUsageSummary> rows) {
        return safeList(rows).stream()
                .filter(Objects::nonNull)
                .peek(this::normalizeBudgetUsage)
                .toList();
    }

    private void normalizeBudgetUsage(BudgetUsageSummary row) {
        BigDecimal budgetAmount = nullToZero(row.getBudgetAmount());
        BigDecimal usedAmount = nullToZero(row.getUsedAmount());
        row.setBudgetAmount(budgetAmount);
        row.setUsedAmount(usedAmount);
        row.setRemainingAmount(amountScale(budgetAmount.subtract(usedAmount)));
        row.setUsagePercent(budgetAmount.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : usedAmount.multiply(BigDecimal.valueOf(100)).divide(budgetAmount, 2, RoundingMode.HALF_UP));
        row.setOverBudget(usedAmount.compareTo(budgetAmount) > 0);
        row.setTransactionCount(nullToZero(row.getTransactionCount()));
    }

    private List<DailySummary> fillDailyTrend(YearMonth month, List<DailySummary> rows) {
        Map<String, DailySummary> rowsByDate = new HashMap<>();
        for (DailySummary row : safeList(rows)) {
            normalizeDailySummary(row);
            rowsByDate.put(row.getDate(), row);
        }
        return IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(day -> rowsByDate.getOrDefault(month.atDay(day).toString(), emptyDailySummary(month, day)))
                .toList();
    }

    private void normalizeDailySummary(DailySummary row) {
        BigDecimal totalExpense = nullToZero(row.getTotalExpense());
        BigDecimal totalIncome = nullToZero(row.getTotalIncome());
        row.setTotalExpense(totalExpense);
        row.setTotalIncome(totalIncome);
        row.setBalance(totalIncome.subtract(totalExpense));
        row.setTransactionCount(nullToZero(row.getTransactionCount()));
    }

    private DailySummary emptyDailySummary(YearMonth month, int day) {
        DailySummary summary = new DailySummary();
        summary.setDate(month.atDay(day).toString());
        summary.setTotalExpense(BigDecimal.ZERO);
        summary.setTotalIncome(BigDecimal.ZERO);
        summary.setBalance(BigDecimal.ZERO);
        summary.setTransactionCount(0L);
        return summary;
    }

    private List<MonthlyTrendSummary> fillMonthlyTrend(Year year, List<MonthlyTrendSummary> rows) {
        Map<String, MonthlyTrendSummary> rowsByMonth = new HashMap<>();
        for (MonthlyTrendSummary row : safeList(rows)) {
            normalizeMonthlyTrendSummary(row);
            rowsByMonth.put(row.getMonth(), row);
        }
        return IntStream.rangeClosed(1, 12)
                .mapToObj(month -> rowsByMonth.getOrDefault(YearMonth.of(year.getValue(), month).toString(), emptyMonthlyTrendSummary(year, month)))
                .toList();
    }

    private void normalizeMonthlyTrendSummary(MonthlyTrendSummary row) {
        BigDecimal totalExpense = nullToZero(row.getTotalExpense());
        BigDecimal totalIncome = nullToZero(row.getTotalIncome());
        row.setTotalExpense(totalExpense);
        row.setTotalIncome(totalIncome);
        row.setBalance(totalIncome.subtract(totalExpense));
        row.setTransactionCount(nullToZero(row.getTransactionCount()));
    }

    private MonthlyTrendSummary emptyMonthlyTrendSummary(Year year, int month) {
        MonthlyTrendSummary summary = new MonthlyTrendSummary();
        summary.setMonth(YearMonth.of(year.getValue(), month).toString());
        summary.setTotalExpense(BigDecimal.ZERO);
        summary.setTotalIncome(BigDecimal.ZERO);
        summary.setBalance(BigDecimal.ZERO);
        summary.setTransactionCount(0L);
        return summary;
    }
}
