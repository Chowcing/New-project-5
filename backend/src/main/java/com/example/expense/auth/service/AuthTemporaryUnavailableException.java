package com.example.expense.auth.service;

public class AuthTemporaryUnavailableException extends RuntimeException {

    public AuthTemporaryUnavailableException() {
        super("认证服务暂时不可用，请稍后再试");
    }

    public AuthTemporaryUnavailableException(Throwable cause) {
        super("认证服务暂时不可用，请稍后再试", cause);
    }
}
