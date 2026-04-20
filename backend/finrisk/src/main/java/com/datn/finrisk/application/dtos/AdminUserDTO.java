package com.datn.finrisk.application.dtos;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminUserDTO {
    private Long id;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String status; // ACTIVE hoặc LOCKED
    private boolean isSuspiciousSession; // Cờ cảnh báo IP
    private String lastLoginIp;
    private String lastLoginDevice;
    private LocalDateTime createdAt;
}