package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.RiskPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

//Transaction B7: dò bản policy xem điểm tương ứng với rủi ro nào va cần làm gì trong trả về kết quả  -> Transaction B8: PinActnStrategy
@Repository
public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Long> {
    
 
    @Query("SELECT p FROM RiskPolicy p WHERE :score >= p.minScore AND :score <= p.maxScore")
    Optional<RiskPolicy> findByScore(@Param("score") int score);

 
    Optional<RiskPolicy> findByRiskLevel(String riskLevel);
}