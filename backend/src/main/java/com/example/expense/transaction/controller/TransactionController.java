package com.example.expense.transaction.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionDayCardsResponse;
import com.example.expense.transaction.dto.TransactionDayOptionResponse;
import com.example.expense.transaction.dto.TransactionRequest;
import com.example.expense.transaction.dto.TransactionResponse;
import com.example.expense.transaction.dto.TransactionTemplateResponse;
import com.example.expense.transaction.entity.ExpenseTransaction;
import com.example.expense.transaction.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/api/v1/transactions")
@Validated
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long paymentMethodId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size
    ) {
        return ApiResponse.ok(transactionService.list(
                SecurityUtils.currentUserId(), type, startDate, endDate, channel, categoryId, paymentMethodId, keyword, page, size));
    }

    @GetMapping("/daily-cards")
    public ApiResponse<TransactionDayCardsResponse> dailyCards(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long paymentMethodId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) Integer dayPage,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) Integer daySize,
            @RequestParam(defaultValue = "1") @Min(1) Integer recordPage,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) Integer recordSize
    ) {
        return ApiResponse.ok(transactionService.dailyCards(
                SecurityUtils.currentUserId(), type, startDate, endDate, channel, categoryId, paymentMethodId, keyword,
                dayPage, daySize, recordPage, recordSize));
    }

    @GetMapping("/daily-options")
    public ApiResponse<List<TransactionDayOptionResponse>> dailyOptions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long paymentMethodId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(transactionService.dailyOptions(
                SecurityUtils.currentUserId(), type, startDate, endDate, channel, categoryId, paymentMethodId, keyword));
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<TransactionResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(transactionService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<TransactionTemplateResponse>> recommendations(
            @RequestParam(defaultValue = "5") @Min(1) @Max(10) Integer limit
    ) {
        return ApiResponse.ok(transactionService.recommendTemplates(SecurityUtils.currentUserId(), limit));
    }

    @GetMapping("/recommendations/context")
    public ApiResponse<List<TransactionTemplateResponse>> contextRecommendations(
            @RequestParam(required = false) String itemName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) Integer limit
    ) {
        return ApiResponse.ok(transactionService.recommendContextTemplates(
                SecurityUtils.currentUserId(), itemName, type, channel, occurredAt, limit));
    }

    @PostMapping
    public ApiResponse<ExpenseTransaction> create(@Valid @RequestBody TransactionRequest request) {
        return ApiResponse.ok("记录已保存", transactionService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<ExpenseTransaction> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest request) {
        return ApiResponse.ok("记录已更新", transactionService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        transactionService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("记录已删除", null);
    }
}
