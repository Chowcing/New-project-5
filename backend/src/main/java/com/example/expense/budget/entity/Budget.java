package com.example.expense.budget.entity;

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

@TableName("budgets")
@Getter
@Setter
public class Budget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String month;
    // null 表示整月预算，分类预算切换为整月预算时必须清空原分类。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    private BigDecimal amount;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
