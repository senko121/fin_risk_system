package com.datn.finrisk.application.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthVerifyRequest {
    
    private Long transactionId; 

    private String authType;

    private String authCode; 

    private String faceImageBase64;
}