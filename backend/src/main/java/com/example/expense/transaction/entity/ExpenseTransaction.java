package com.example.expense.transaction.entity;

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
import java.time.LocalDateTime;

@TableName("transactions")
@Getter
@Setter
public class ExpenseTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    // 编辑先读取完整实体；这些可清空字段的 null 必须写回数据库。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String itemName;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
    private String channel;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String onlineApp;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long onlinePlatformId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offlinePlace;
    private Long paymentMethodId;
    private String paymentMethodName;
    private Long categoryId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String note;
    private LocalDateTime trashedAt;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
