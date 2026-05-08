package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional; 
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.fromAccount.id = :accountId AND t.createdAt >= :timeLimit")
    int countRecentTransactions(Long accountId, LocalDateTime timeLimit);
    
    @Query("SELECT COUNT(t) FROM Transaction t")
    long countTotalTransactions();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.riskLevel = 'HIGH'")
    long countHighRiskTransactions();

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.status = 'SUCCESS'")
    BigDecimal sumTotalSuccessfulAmount();

    List<Transaction> findTop5ByFromAccountUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.fromAccount.id = :accountId AND t.status = 'SUCCESS' AND t.createdAt >= :startOfDay")
    BigDecimal sumSuccessfulAmountToday(@Param("accountId") Long accountId, @Param("startOfDay") LocalDateTime startOfDay);

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.fromAccount a
        LEFT JOIN FETCH a.user u
        LEFT JOIN FETCH u.userSecurity
        WHERE t.id = :id
    """)
    Optional<Transaction> findByIdWithUserSecurity(@Param("id") Long id);
}