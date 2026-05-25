package com.example.expense.statistics.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ChannelSummary {
    private String channel;
    private BigDecimal amount;
    private Long transactionCount;

}
