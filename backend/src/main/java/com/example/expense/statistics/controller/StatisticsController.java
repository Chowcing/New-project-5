package com.example.expense.statistics.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.statistics.dto.MonthlyStatisticsResponse;
import com.example.expense.statistics.dto.YearlyStatisticsResponse;
import com.example.expense.statistics.service.StatisticsService;
import java.time.Year;
import java.time.YearMonth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/monthly")
    public ApiResponse<MonthlyStatisticsResponse> monthly(@RequestParam String month) {
        return ApiResponse.ok(statisticsService.monthly(SecurityUtils.currentUserId(), YearMonth.parse(month)));
    }

    @GetMapping("/yearly")
    public ApiResponse<YearlyStatisticsResponse> yearly(@RequestParam int year) {
        return ApiResponse.ok(statisticsService.yearly(SecurityUtils.currentUserId(), Year.of(year)));
    }
}
