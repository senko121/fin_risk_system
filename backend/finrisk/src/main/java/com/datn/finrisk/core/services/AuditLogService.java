package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.AuditLog;
import com.datn.finrisk.core.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

//Transaction B10: ghi nhật ký bắt đâu giao dịch
//Transaction B10 Phase 2: ghi nhật ký  chốt sổ giao dịch hoan tất success
@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;
 
    public void logAction(String username, String action, String details) {
        try {
            AuditLog log = new AuditLog(
                username != null ? username : "SYSTEM/UNKNOWN",
                action,
                details
            );
            
            auditLogRepository.save(log);
            System.out.println("📝 AUDIT LOG: [" + action + "] - User: " + log.getUsername());
        } catch (Exception e) {
            System.err.println("❌ Lỗi ghi Audit Log: " + e.getMessage());
        }
    }
}