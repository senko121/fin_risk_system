package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.TransactionLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransactionLedgerRepository extends JpaRepository<TransactionLedger, Long> {
    List<TransactionLedger> findByAccountIdOrderByCreatedAtDesc(Long accountId);
}