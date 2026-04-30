package com.example.expense.statistics.service;

import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.MonthlyStatisticsResponse;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.mapper.StatisticsMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
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
        List<CategorySummary> expenseByCategory = statisticsMapper.selectCategorySummary(userId, "EXPENSE", startAt, endAt);
        List<CategorySummary> incomeByCategory = statisticsMapper.selectCategorySummary(userId, "INCOME", startAt, endAt);
        return new MonthlyStatisticsResponse(
                month.toString(),
                totalExpense,
                totalIncome,
                totalIncome.subtract(totalExpense),
                expenseByCategory,
                incomeByCategory
        );
    }

    private BigDecimal nullToZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}

