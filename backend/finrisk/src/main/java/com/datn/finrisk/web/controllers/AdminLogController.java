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

 
    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
 
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        
 
        Page<AuditLog> logPage = auditLogRepo.findAll(pageable);
        
        return ResponseEntity.ok(logPage);
    }
 
    @GetMapping("/config")
    public ResponseEntity<Page<SystemConfigLog>> getConfigLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
 
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
 
        Page<SystemConfigLog> logPage = configLogRepo.findAll(pageable);
        
        return ResponseEntity.ok(logPage);
    }
}