package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {
}