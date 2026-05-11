package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.util.List; 

@Data
public class AuthVerifyRequest {
    private Long transactionId; 
    private String authType;    
    private String authCode;    
 
    private String faceImageBase64; 
 
    private List<String> faceFrameSequence; 
}