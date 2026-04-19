package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class AuthVerifyRequest {
    private Long transactionId; // ID của giao dịch đang bị treo
    private String authType;    // Loại xác thực: "OTP" hoặc "FACE"
    private String authCode;    // Mã người dùng nhập (VD: "123456")
    private String faceImageBase64;
}