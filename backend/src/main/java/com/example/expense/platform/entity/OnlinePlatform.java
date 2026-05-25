package com.example.expense.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@TableName("online_platforms")
@Getter
@Setter
public class OnlinePlatform {
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
