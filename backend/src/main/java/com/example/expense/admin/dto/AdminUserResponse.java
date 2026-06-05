package com.example.expense.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AdminUserResponse {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private LocalDateTime emailVerifiedAt;
    private String status;
    private boolean admin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long transactionCount;

}
