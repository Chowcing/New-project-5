package com.example.expense.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.auth.service.AuthTemporaryUnavailableException;
import com.example.expense.transaction.ai.service.AiSceneRateLimitException;
import com.example.expense.transaction.ai.service.AiSceneUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    @Test
    void authTemporaryUnavailableReturnsServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleAuthTemporaryUnavailable(new AuthTemporaryUnavailableException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("认证服务暂时不可用，请稍后再试");
    }

    @Test
    void aiSceneRateLimitReturnsSafeTooManyRequestsMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleAiSceneRateLimit(
                new AiSceneRateLimitException("redis-key=user:1001"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("AI 分类请求过于频繁，请稍后再试");
        assertThat(response.getBody().message()).doesNotContain("user:1001");
    }

    @Test
    void aiSceneUnavailableReturnsSafeServiceUnavailableMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleAiSceneUnavailable(
                new AiSceneUnavailableException("provider api-key=secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).isEqualTo("AI 分类服务暂时不可用");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
