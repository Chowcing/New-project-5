package com.example.expense.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdminAuditLogResponse {
    private Long id;
    private Long adminUserId;
    private String adminUsername;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private LocalDateTime createdAt;

}
