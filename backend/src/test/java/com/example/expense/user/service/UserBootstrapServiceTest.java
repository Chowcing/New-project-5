package com.example.expense.user.service;

import static org.mockito.Mockito.verify;

import com.example.expense.category.service.CategoryService;
import com.example.expense.payment.service.PaymentMethodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserBootstrapServiceTest {
    @Mock
    private CategoryService categoryService;
    @Mock
    private PaymentMethodService paymentMethodService;

    @Test
    void bootstrapDefaultDataCallsCategoryAndPaymentMethodServices() {
        UserBootstrapService service = new UserBootstrapService(categoryService, paymentMethodService);

        service.bootstrapDefaultData(1001L);

        verify(categoryService).createDefaults(1001L);
        verify(paymentMethodService).createDefaults(1001L);
    }
}
