package com.example.expense.statistics.mapper;

import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.BudgetUsageSummary;
import com.example.expense.statistics.dto.ChannelSummary;
import com.example.expense.statistics.dto.DailySummary;
import com.example.expense.statistics.dto.MonthlyTotals;
import com.example.expense.statistics.dto.MonthlyTrendSummary;
import com.example.expense.statistics.dto.PaymentMethodSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface StatisticsMapper {
    MonthlyTotals selectMonthlyTotals(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<CategorySummary> selectCategorySummary(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<DailySummary> selectDailySummary(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<MonthlyTrendSummary> selectMonthlyTrend(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<ChannelSummary> selectExpenseByChannel(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<PaymentMethodSummary> selectExpenseByPaymentMethod(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<BudgetUsageSummary> selectBudgetUsage(
            @Param("userId") Long userId,
            @Param("month") String month,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
