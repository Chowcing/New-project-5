package com.example.expense.budget.controller;

import com.example.expense.budget.dto.BudgetRequest;
import com.example.expense.budget.entity.Budget;
import com.example.expense.budget.service.BudgetService;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ApiResponse<List<Budget>> list(@RequestParam(required = false) String month) {
        return ApiResponse.ok(budgetService.list(SecurityUtils.currentUserId(), month));
    }

    @PostMapping
    public ApiResponse<Budget> create(@Valid @RequestBody BudgetRequest request) {
        return ApiResponse.ok("预算已创建", budgetService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Budget> update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return ApiResponse.ok("预算已更新", budgetService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        budgetService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("预算已删除", null);
    }
}

