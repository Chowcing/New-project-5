package com.example.expense.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentMethodSummary {
    private Long paymentMethodId;
    private String paymentMethodName;
    private BigDecimal amount;
    private Long transactionCount;

}
