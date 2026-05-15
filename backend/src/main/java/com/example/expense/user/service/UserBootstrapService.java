package com.example.expense.user.service;

import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBootstrapService {
    private final CategoryService categoryService;
    private final PaymentMethodService paymentMethodService;

    public UserBootstrapService(CategoryService categoryService, PaymentMethodService paymentMethodService) {
        this.categoryService = categoryService;
        this.paymentMethodService = paymentMethodService;
    }

    @Transactional
    public void bootstrapDefaultData(Long userId) {
        categoryService.createDefaults(userId);
        paymentMethodService.createDefaults(userId);
    }
}
