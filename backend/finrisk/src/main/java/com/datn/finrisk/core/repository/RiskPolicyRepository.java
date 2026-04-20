package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Long> {
    
    // TRÁI TIM CỦA VIỆC TÌM KIẾM ĐỘNG: Tìm policy dựa vào tổng điểm
    @Query("SELECT p FROM RiskPolicy p WHERE :score >= p.minScore AND :score <= p.maxScore")
    Optional<RiskPolicy> findByScore(@Param("score") int score);
}