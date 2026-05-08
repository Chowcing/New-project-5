package com.example.expense.statistics.service;

import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.ChannelSummary;
import com.example.expense.statistics.dto.DailySummary;
import com.example.expense.statistics.dto.MonthlyStatisticsResponse;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.dto.PaymentMethodSummary;
import com.example.expense.statistics.mapper.StatisticsMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        // 月度统计全部在数据库侧聚合，避免把整月明细拉回 JVM 后再计算。
        MonthlyTotals totals = statisticsMapper.selectMonthlyTotals(userId, startAt, endAt);
        BigDecimal totalExpense = nullToZero(totals.getTotalExpense());
        BigDecimal totalIncome = nullToZero(totals.getTotalIncome());
        List<DailySummary> dailyTrend = fillDailyTrend(month, statisticsMapper.selectDailySummary(userId, startAt, endAt));
        List<CategorySummary> expenseByCategory = statisticsMapper.selectCategorySummary(userId, "EXPENSE", startAt, endAt);
        List<CategorySummary> incomeByCategory = statisticsMapper.selectCategorySummary(userId, "INCOME", startAt, endAt);
        List<ChannelSummary> expenseByChannel = statisticsMapper.selectExpenseByChannel(userId, startAt, endAt);
        List<PaymentMethodSummary> expenseByPaymentMethod = statisticsMapper.selectExpenseByPaymentMethod(userId, startAt, endAt);
        return new MonthlyStatisticsResponse(
                month.toString(),
                totalExpense,
                totalIncome,
                totalIncome.subtract(totalExpense),
                nullToZero(totals.getTransactionCount()),
                nullToZero(totals.getExpenseCount()),
                nullToZero(totals.getIncomeCount()),
                dailyTrend,
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

    private List<DailySummary> fillDailyTrend(YearMonth month, List<DailySummary> rows) {
        Map<String, DailySummary> rowsByDate = new HashMap<>();
        for (DailySummary row : rows) {
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
}
