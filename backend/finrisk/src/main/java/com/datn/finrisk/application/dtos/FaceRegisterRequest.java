package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class FaceRegisterRequest {
    private Long userId;
    private String base64FaceImage; // Chuỗi 128 số lấy từ React
}