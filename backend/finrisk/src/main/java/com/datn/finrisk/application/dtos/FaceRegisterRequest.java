package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class FaceRegisterRequest {
    private Long userId;
    private String base64FaceImage;  
}