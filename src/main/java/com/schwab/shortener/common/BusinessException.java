package com.schwab.shortener.common;

public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
