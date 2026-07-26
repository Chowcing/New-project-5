package com.example.expense.transaction.ai.service;

public class AiSceneUnavailableException extends RuntimeException {

    public AiSceneUnavailableException(String message) {
        super(message);
    }

    public AiSceneUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
