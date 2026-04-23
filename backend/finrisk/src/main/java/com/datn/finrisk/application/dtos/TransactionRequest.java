package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class TransactionRequest {
    private Long fromAccountId;
    private String toAccount; 
    
    @NotNull(message = "Số tiền không được để trống")
    @Min(value = 10000, message = "LỖI BẢO MẬT: Số tiền giao dịch phải lớn hơn 10.000 VND")
    private BigDecimal amount;  
    
    private String description;
}