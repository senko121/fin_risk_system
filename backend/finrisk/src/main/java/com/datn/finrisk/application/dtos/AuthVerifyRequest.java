package com.datn.finrisk.application.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthVerifyRequest {
    
    private Long transactionId; 

    @NotBlank(message = "Lỗi: Loại xác thực không được để trống")
    private String authType;

    @NotBlank(message = "Lỗi: Mã xác thực không được để trống")
    private String authCode; 

    private String faceImageBase64;
}