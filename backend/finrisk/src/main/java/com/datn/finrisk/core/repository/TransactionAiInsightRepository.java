package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.TransactionAiInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionAiInsightRepository extends JpaRepository<TransactionAiInsight, Long> {
 
    List<TransactionAiInsight> findByTransactionIdIn(List<Long> transactionIds);
}