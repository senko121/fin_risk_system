package com.datn.finrisk.core.exceptions;

public class InvalidTransactionException extends BusinessLogicException {
    public InvalidTransactionException(String message) {
        super("ERR_INVALID_TRANSACTION", message);
    }
}