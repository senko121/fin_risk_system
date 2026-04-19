package com.datn.finrisk.core.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ai là người thực hiện?
    @Column(nullable = false)
    private String username;

    // Hành động là gì? (VD: LOGIN_SUCCESS, TRANSFER_FAILED, FACE_SCAN_FAILED)
    @Column(nullable = false)
    private String action;

    // Thông tin thêm (VD: IP, chi tiết lỗi)
    @Column(columnDefinition = "TEXT")
    private String details;

    // Thời gian xảy ra
    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public AuditLog(String username, String action, String details) {
        this.username = username;
        this.action = action;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}