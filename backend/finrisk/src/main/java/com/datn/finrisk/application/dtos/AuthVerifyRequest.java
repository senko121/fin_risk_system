package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class AuthVerifyRequest {
    private Long transactionId; 
    private String authType;    // Có thể truyền lên: "PIN", "OTP", hoặc "FACE"
    private String authCode;    // Mật mã người dùng nhập (6 số PIN hoặc 6 số OTP)
    private String faceImageBase64;
}