package com.example.expense.imports.entity;

import lombok.Getter;
import lombok.Setter;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@TableName("import_jobs")
@Getter
@Setter
public class ImportJob {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String originalFilename;
    private String contentHash;
    @JsonIgnore
    private String csvContent;
    private String status;
    private Integer totalRows;
    private Integer importedRows;
    private Integer failedRows;
    private String resultJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;

}
