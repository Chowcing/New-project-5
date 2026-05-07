package com.example.expense.payment.controller;

import com.example.expense.common.security.SecurityUtils;
import com.example.expense.common.web.ApiResponse;
import com.example.expense.payment.dto.PaymentMethodRequest;
import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.service.PaymentMethodService;
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
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodService paymentMethodService;

    public PaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public ApiResponse<List<PaymentMethod>> list() {
        return ApiResponse.ok(paymentMethodService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ApiResponse<PaymentMethod> create(@Valid @RequestBody PaymentMethodRequest request) {
        return ApiResponse.ok("支付方式已创建", paymentMethodService.create(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PaymentMethod> update(@PathVariable Long id, @Valid @RequestBody PaymentMethodRequest request) {
        return ApiResponse.ok("支付方式已更新", paymentMethodService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paymentMethodService.delete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("支付方式已删除", null);
    }
}

