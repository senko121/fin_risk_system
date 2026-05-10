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

//Transaction B5 cung cấp dữ liệu thốn kê cho rule engine tín điểm bao gồm đếm sgd gần đây countRecentTransactions và tính tổng sumSuccessfulAmountToday
//  -> Transaction B6: RickEvaluationService

//Transaction B5 Phase 2: lôi lại cái giao dịch ở trạng thái pendingg lên findByIdWithUserSecurity  -> Transaction B11: Pinservice
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