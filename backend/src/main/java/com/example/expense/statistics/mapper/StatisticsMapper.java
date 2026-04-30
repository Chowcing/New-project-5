package com.example.expense.statistics.mapper;

import com.example.expense.statistics.dto.CategorySummary;
import com.example.expense.statistics.dto.MonthlyTotals;
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
}

