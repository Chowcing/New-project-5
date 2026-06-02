package com.example.expense.auth.service;

public class LoginRateLimitException extends RuntimeException {
    public LoginRateLimitException(String message) {
        super(message);
    }
}
