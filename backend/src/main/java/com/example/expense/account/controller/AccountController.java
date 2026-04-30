package com.example.expense.account.controller;

import com.example.expense.account.dto.AccountRequest;
import com.example.expense.account.entity.Account;
import com.example.expense.account.service.AccountService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<Account>> list() {
        return ApiResponse.ok(accountService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ApiResponse<Account> create(@Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok("账户已创建", accountService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Account> update(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        return ApiResponse.ok("账户已更新", accountService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accountService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("账户已删除", null);
    }
}

