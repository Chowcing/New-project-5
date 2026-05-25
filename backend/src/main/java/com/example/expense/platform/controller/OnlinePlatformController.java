package com.example.expense.platform.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.platform.dto.OnlinePlatformRequest;
import com.example.expense.platform.entity.OnlinePlatform;
import com.example.expense.platform.service.OnlinePlatformService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/online-platforms")
@Validated
public class OnlinePlatformController {
    private final OnlinePlatformService onlinePlatformService;

    public OnlinePlatformController(OnlinePlatformService onlinePlatformService) {
        this.onlinePlatformService = onlinePlatformService;
    }

    @GetMapping
    public ApiResponse<List<OnlinePlatform>> list() {
        return ApiResponse.ok(onlinePlatformService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ApiResponse<OnlinePlatform> create(@Valid @RequestBody OnlinePlatformRequest request) {
        return ApiResponse.ok("线上平台已创建", onlinePlatformService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OnlinePlatform> update(@PathVariable Long id, @Valid @RequestBody OnlinePlatformRequest request) {
        return ApiResponse.ok("线上平台已更新", onlinePlatformService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        onlinePlatformService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("线上平台已删除", null);
    }
}
