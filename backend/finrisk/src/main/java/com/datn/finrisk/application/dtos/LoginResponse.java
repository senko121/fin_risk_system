package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LoginResponse {
    private Long userId;
    private String fullName;
    private String accountNumber;
    private BigDecimal balance;
    private String message;
}