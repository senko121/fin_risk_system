package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;  
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, Long> {

    
    List<RiskScore> findByTransactionId(Long transactionId);
 
    @Query("""
        SELECT rs FROM RiskScore rs
        JOIN FETCH rs.rule
        JOIN rs.transaction t
        WHERE t.id IN :txIds
    """)
    List<RiskScore> findByTransactionIdInWithRule(@Param("txIds") List<Long> txIds);
}