package com.example.expense.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@TableName("auth_challenges")
@Getter
@Setter
public class AuthChallenge {
    @TableId(type = IdType.AUTO)
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
