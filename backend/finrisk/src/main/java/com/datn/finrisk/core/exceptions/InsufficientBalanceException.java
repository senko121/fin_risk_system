package com.datn.finrisk.core.exceptions;

public class InsufficientBalanceException extends BusinessLogicException {
    public InsufficientBalanceException(String message) {
        super("ERR_INSUFFICIENT_BALANCE", message);
    }
}