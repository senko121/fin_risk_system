package com.datn.finrisk.core.exceptions;

public class BusinessLogicException extends RuntimeException {
    private final String errorCode;

    public BusinessLogicException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}