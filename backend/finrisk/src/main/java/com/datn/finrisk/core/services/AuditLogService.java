package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.AuditLog;
import com.datn.finrisk.core.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;
 
    public void logAction(String username, String action, String details) {
        try {
            AuditLog entry = new AuditLog(
                username != null ? username : "SYSTEM/UNKNOWN",
                action,
                details
            );
            auditLogRepository.save(entry);
            log.info("[AUDIT] action={} user={}", action, entry.getUsername());
        } catch (Exception e) {
            log.error("[AUDIT] Failed to persist audit log action={} user={}: {}", action, username, e.getMessage());
        }
    }
}