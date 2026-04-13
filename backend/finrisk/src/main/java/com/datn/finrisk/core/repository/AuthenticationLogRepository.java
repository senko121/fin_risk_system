package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.AuthenticationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthenticationLogRepository extends JpaRepository<AuthenticationLog, Long> {
}