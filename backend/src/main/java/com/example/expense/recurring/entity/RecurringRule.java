package com.example.expense.recurring.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
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
    // 规则更新使用完整实体，允许清空的表单字段及结束后的下次日期必须持久化 null。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String itemName;
    private BigDecimal amount;
    private String channel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String onlineApp;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    private String categoryName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    private String scheduleType;
    private Integer intervalValue;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer dayOfMonth;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String weekday;
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate nextRunDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate endDate;
    private Integer reminderDaysBefore;
    private String status;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

