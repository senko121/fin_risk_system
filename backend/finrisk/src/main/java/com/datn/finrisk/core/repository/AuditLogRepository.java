package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
 
    List<AuditLog> findAllByOrderByTimestampDesc();
 
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
}