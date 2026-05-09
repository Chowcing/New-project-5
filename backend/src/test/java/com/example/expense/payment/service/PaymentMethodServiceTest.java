package com.example.expense.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.expense.payment.entity.PaymentMethod;
import com.example.expense.payment.mapper.PaymentMethodMapper;
import com.example.expense.transaction.mapper.TransactionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMethodServiceTest {
    @Mock
    private PaymentMethodMapper paymentMethodMapper;
    @Mock
    private TransactionMapper transactionMapper;

    @Test
    void createDefaultsCreatesCommonPaymentMethods() {
        PaymentMethodService service = new PaymentMethodService(paymentMethodMapper, transactionMapper);

        service.createDefaults(1001L);

        ArgumentCaptor<PaymentMethod> methodCaptor = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(paymentMethodMapper, times(8)).insert(methodCaptor.capture());
        List<PaymentMethod> methods = methodCaptor.getAllValues();
        assertThat(methods).extracting(PaymentMethod::getName).containsExactly(
                "微信", "支付宝", "银行卡", "信用卡", "借记卡", "现金", "云闪付", "其他"
        );
        assertThat(methods).extracting(PaymentMethod::getUserId).containsOnly(1001L);
        assertThat(methods).extracting(PaymentMethod::getName).doesNotContain("银行卡转账");
    }
}
