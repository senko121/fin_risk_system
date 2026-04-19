package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransactionRequest {
    private Long fromAccountId; // Lấy từ người đang đăng nhập
    private String toAccount;   // Số tài khoản người nhận
    private BigDecimal amount;  // Số tiền
    private String emotion;     // Cảm xúc lấy từ Camera React
    private String description;
}