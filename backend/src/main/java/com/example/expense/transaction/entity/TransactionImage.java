package com.example.expense.transaction.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("transaction_images")
@Getter
@Setter
public class TransactionImage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long transactionId;
    private String originalFilename;
    private String storedFilename;
    private String relativePath;
    private String contentType;
    private Long sizeBytes;
    private Integer sortOrder;
    @TableLogic
    private Integer deleted;
    private LocalDateTime physicalDeletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
