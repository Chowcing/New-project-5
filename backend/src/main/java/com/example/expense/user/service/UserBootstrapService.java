package com.example.expense.user.service;

import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import com.example.expense.platform.service.OnlinePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBootstrapService {
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;
    private final OnlinePlatformService onlinePlatformService;

    public UserBootstrapService(
            CategoryService categoryService,
            PaymentMethodService paymentMethodService,
            OnlinePlatformService onlinePlatformService
    ) {
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
        this.onlinePlatformService = onlinePlatformService;
    }

    @Transactional
    public void bootstrapDefaultData(Long userId) {
        categoryService.createDefaults(userId);
        paymentMethodService.createDefaults(userId);
        onlinePlatformService.createDefaults(userId);
    }
}
