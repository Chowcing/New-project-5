package com.example.expense.auth.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthChallenge {
    private Long id;
    private String challengeId;
    private Long userId;
    private String email;
    private String purpose;
    private String codeHash;
    private LocalDateTime expiresAt;
    private Integer attemptCount;
    private LocalDateTime consumedAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
