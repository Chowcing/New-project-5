package com.example.expense.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.auth.service.AuthTemporaryUnavailableException;
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
}
