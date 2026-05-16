package com.example.expense.recurring.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.recurring.dto.RecurringRuleRequest;
import com.example.expense.recurring.entity.RecurringRule;
import com.example.expense.recurring.entity.RecurringRuleRun;
import com.example.expense.recurring.service.RecurringRuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring-rules")
public class RecurringRuleController {
    private final RecurringRuleService recurringRuleService;

    public RecurringRuleController(RecurringRuleService recurringRuleService) {
        this.recurringRuleService = recurringRuleService;
    }

    @GetMapping
    public ApiResponse<List<RecurringRule>> list() {
        return ApiResponse.ok(recurringRuleService.listRules(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecurringRule> get(@PathVariable Long id) {
        return ApiResponse.ok(recurringRuleService.getRule(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/runs")
    public ApiResponse<List<RecurringRuleRun>> runs(@PathVariable Long id) {
        return ApiResponse.ok(recurringRuleService.listRuns(SecurityUtils.currentUserId(), id));
    }

    @PostMapping
    public ApiResponse<RecurringRule> create(@Valid @RequestBody RecurringRuleRequest request) {
        return ApiResponse.ok("周期规则已创建", recurringRuleService.createRule(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<RecurringRule> update(@PathVariable Long id, @Valid @RequestBody RecurringRuleRequest request) {
        return ApiResponse.ok("周期规则已更新", recurringRuleService.updateRule(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        recurringRuleService.deleteRule(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("周期规则已删除", null);
    }
}

