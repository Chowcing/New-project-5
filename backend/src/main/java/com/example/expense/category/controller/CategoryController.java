package com.example.expense.category.controller;

import com.example.expense.category.dto.CategoryRequest;
import com.example.expense.category.entity.Category;
import com.example.expense.category.service.CategoryService;
import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.common.web.PageResponse;
import com.example.expense.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
@RequestMapping("/api/v1/categories")
@Validated
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<Category>> list(@RequestParam(required = false) String type) {
        return ApiResponse.ok(categoryService.list(SecurityUtils.currentUserId(), type));
    }

    @PostMapping
    public ApiResponse<Category> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("分类已创建", categoryService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok("分类已更新", categoryService.update(SecurityUtils.currentUserId(), id, request));
    }

    @GetMapping("/{id}/references")
    public ApiResponse<PageResponse<TransactionResponse>> references(
            @PathVariable Long id,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) Integer size
    ) {
        return ApiResponse.ok(categoryService.references(SecurityUtils.currentUserId(), id, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        categoryService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("分类已删除", null);
    }
}
