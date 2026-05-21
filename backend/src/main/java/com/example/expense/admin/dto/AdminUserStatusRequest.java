package com.example.expense.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserStatusRequest(
        @NotBlank(message = "不能为空")
        String status,
        @Size(max = 255, message = "不能超过 255 个字符")
        String reason
) {
}
