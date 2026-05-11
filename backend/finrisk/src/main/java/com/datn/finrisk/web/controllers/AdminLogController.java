package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.entities.AuditLog;
import com.datn.finrisk.core.entities.SystemConfigLog;
import com.datn.finrisk.core.repository.AuditLogRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logs")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminLogController {

    @Autowired private AuditLogRepository auditLogRepo;
    @Autowired private SystemConfigLogRepository configLogRepo;

    // 🚀 API 1: Audit Logs (Nhật ký thao tác) - Có phân trang
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // Sắp xếp theo cột "timestamp" mới nhất
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
        // Dùng luôn hàm findAll(pageable) mặc định của JpaRepository
        Page<AuditLog> logPage = auditLogRepo.findAll(pageable);
        
        return ResponseEntity.ok(logPage);
    }

    // 🚀 API 2: Config Logs (Nhật ký hệ thống) - Có phân trang
    @GetMapping("/config")
    public ResponseEntity<Page<SystemConfigLog>> getConfigLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // Sắp xếp theo cột "createdAt" mới nhất
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        // Dùng luôn hàm findAll(pageable) mặc định của JpaRepository
        Page<SystemConfigLog> logPage = configLogRepo.findAll(pageable);
        
        return ResponseEntity.ok(logPage);
    }
}