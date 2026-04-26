package com.datn.finrisk.application.dtos;

import lombok.Data;

@Data
public class PinSetupRequest {
    private Long userId;
    private String oldPin; // Null nếu là cài đặt lần đầu
    private String newPin;
}