package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :accountId AND t.createdAt >= :timeLimit")
    int countRecentTransactions(Long accountId, LocalDateTime timeLimit);
    @Query("SELECT COUNT(t) FROM Transaction t")
    long countTotalTransactions();

    // Đếm số giao dịch bị Rủi ro cao (HIGH)
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.riskLevel = 'HIGH'")
    long countHighRiskTransactions();

    // Tính tổng số tiền đã luân chuyển thành công (SUCCESS)
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.status = 'SUCCESS'")
    BigDecimal sumTotalSuccessfulAmount();
}