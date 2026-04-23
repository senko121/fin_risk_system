package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.SystemConfigLog;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigLogRepository extends JpaRepository<SystemConfigLog, Long> {
// Lấy tất cả lịch sử sửa Rules/Policies, mới nhất lên đầu
    List<SystemConfigLog> findAllByOrderByCreatedAtDesc();
}