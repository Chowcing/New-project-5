package com.example.expense.recurring.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("recurring_rules")
@Getter
@Setter
public class RecurringRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String type;
    private String itemName;
    private BigDecimal amount;
    private String channel;
    private String onlineApp;
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    private String categoryName;
    private String note;
    private String scheduleType;
    private Integer intervalValue;
    private Integer dayOfMonth;
    private String weekday;
    private LocalDate startDate;
    private LocalDate nextRunDate;
    private LocalDate endDate;
    private Integer reminderDaysBefore;
    private String status;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

