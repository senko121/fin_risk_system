package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.AiScanLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiScanLogRepository extends JpaRepository<AiScanLog, Long> {
}