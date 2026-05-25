package com.example.expense.payment.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@TableName("payment_methods")
@Getter
@Setter
public class PaymentMethod {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String icon;
    private Integer sortOrder;
    private Boolean pinned;
    @TableLogic
    @JsonIgnore
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
