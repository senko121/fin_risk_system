package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Thêm hàm lấy log theo username, sắp xếp mới nhất lên đầu
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
}