package com.datn.finrisk.web.controllers;

import com.datn.finrisk.core.repository.AuditLogRepository;
import com.datn.finrisk.core.repository.SystemConfigLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/logs")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminLogController {

    @Autowired private AuditLogRepository auditLogRepo;
    @Autowired private SystemConfigLogRepository configLogRepo;

    @GetMapping("/audit")
    public ResponseEntity<?> getAuditLogs() {
        // Lấy 100 log mới nhất
        return ResponseEntity.ok(auditLogRepo.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfigLogs() {
        return ResponseEntity.ok(configLogRepo.findAllByOrderByCreatedAtDesc());
    }
}