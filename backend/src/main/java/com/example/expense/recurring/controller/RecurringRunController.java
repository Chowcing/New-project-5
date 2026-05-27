package com.example.expense.recurring.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.service.RecurringRuleService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring-runs")
public class RecurringRunController {
    private final RecurringRuleService recurringRuleService;
    private final Clock clock;

    public RecurringRunController(RecurringRuleService recurringRuleService, Clock clock) {
        this.recurringRuleService = recurringRuleService;
        this.clock = clock;
    }

    @GetMapping("/due")
    public ApiResponse<List<RecurringRuleRun>> due(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date == null ? LocalDate.now(clock) : date;
        return ApiResponse.ok(recurringRuleService.listDueRuns(SecurityUtils.currentUserId(), targetDate));
    }

    @PostMapping("/{id:\\d+}/generate")
    public ApiResponse<RecurringRuleRun> generate(@PathVariable Long id) {
        return ApiResponse.ok("本次周期记录已生成", recurringRuleService.generateRun(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id:\\d+}/skip")
    public ApiResponse<RecurringRuleRun> skip(@PathVariable Long id) {
        return ApiResponse.ok("本次周期记录已跳过", recurringRuleService.skipRun(SecurityUtils.currentUserId(), id));
    }
}
