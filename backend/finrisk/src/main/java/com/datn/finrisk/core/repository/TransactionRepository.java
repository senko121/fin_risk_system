package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional; 
 
//Transaction B5 cung cấp dữ liệu thốn kê cho rule engine tín điểm bao gồm đếm sgd gần đây countRecentTransactions và tính tổng sumSuccessfulAmountToday
//  -> Transaction B6: RickEvaluationService

//Transaction B5 Phase 2: lôi lại cái giao dịch ở trạng thái pendingg lên findByIdWithUserSecurity  -> Transaction B11: Pinservice
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

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

 
    @Query("SELECT t FROM Transaction t " +
           "LEFT JOIN FETCH t.fromAccount a " +
           "LEFT JOIN FETCH a.user " +
           "WHERE t.status IN " +
           "('PENDING_PIN', 'PENDING_OTP', 'PENDING_FACE_STATIC', " +
           "'PENDING_ALL_IN_ONE', 'PENDING_VOICE_OTP', 'PENDING_PIN_OTP', " +
           "'PENDING_PIN_FACE', 'PENDING_PIN_HIGH') " +
           "AND t.createdAt < :threshold")
    List<Transaction> findStalledTransactions(@Param("threshold") LocalDateTime threshold);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.status = 'PROCESSING' WHERE t.id = :id " +
           "AND t.status IN ('PENDING_PIN', 'PENDING_OTP', 'PENDING_FACE_STATIC', " +
           "'PENDING_ALL_IN_ONE', 'PENDING_VOICE_OTP', 'UNDER_REVIEW')")
    int claimForExecution(@Param("id") Long id);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.status = 'PROCESSING_REVERSAL' WHERE t.id = :id AND t.status = 'SUCCESS'")
    int claimForReversal(@Param("id") Long id);

    // Atomic guard for admin REJECT_FRAUD: only transitions UNDER_REVIEW → BLOCKED.
    // Returns 1 on success, 0 if the status was already changed by another admin (race lost).
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Transaction t SET t.status = 'BLOCKED' WHERE t.id = :id AND t.status = 'UNDER_REVIEW'")
    int blockIfUnderReview(@Param("id") Long id);

    @Transactional
    @Modifying
        @Query("UPDATE Transaction t SET t.emotionSignal = :emotion WHERE t.id = :id")
        void updateEmotionSignal(@Param("id") Long id, @Param("emotion") String emotion);

       
    @Query("SELECT u.fullName FROM Account a JOIN a.user u WHERE a.accountNumber = :accNum")
    Optional<String> findRecipientNameByAccountNumber(@Param("accNum") String accNum);

 
    @Query("SELECT a.accountNumber, u.fullName FROM Account a JOIN a.user u WHERE a.accountNumber IN :accNums")
    List<Object[]> findRecipientNamesBulk(@Param("accNums") List<String> accNums);
}