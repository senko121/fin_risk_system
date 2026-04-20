package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.SystemConfigLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemConfigLogRepository extends JpaRepository<SystemConfigLog, Long> {
}