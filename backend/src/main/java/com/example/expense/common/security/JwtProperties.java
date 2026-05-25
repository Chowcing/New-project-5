package com.example.expense.common.security;

import lombok.Getter;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {
    @NotBlank
    @Size(min = 32, message = "JWT secret 至少需要 32 个字符")
    private String secret;
    @Positive
    private long accessTokenMinutes;
    @Positive
    private long refreshTokenDays;

}
