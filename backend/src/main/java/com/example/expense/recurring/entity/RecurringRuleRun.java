package com.example.expense.recurring.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("recurring_rule_runs")
@Getter
@Setter
public class RecurringRuleRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long ruleId;
    private String ruleName;
    private LocalDate dueDate;
    private Integer reminderDaysBefore;
    private String type;
    // 从完整规则同步快照时，清空值也必须覆盖实例中的旧内容。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String itemName;
    private BigDecimal amount;
    private String channel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String onlineApp;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offlinePlace;
    private Long paymentMethodId;
    private Long categoryId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    private String status;
    private Long transactionId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}

