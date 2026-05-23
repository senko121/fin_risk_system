 

package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.TransactionLedger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;  
import java.util.List;

@Repository
public interface TransactionLedgerRepository extends JpaRepository<TransactionLedger, Long> {
    
    List<TransactionLedger> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query(
        value = "SELECT tl FROM TransactionLedger tl " +
                "JOIN FETCH tl.transaction t " +
                "JOIN FETCH t.fromAccount a " +
                "WHERE tl.account.id = :accountId " +
                "AND (:entryType IS NULL OR tl.entryType = :entryType)",
        countQuery = "SELECT count(tl) FROM TransactionLedger tl " +
                     "WHERE tl.account.id = :accountId " +
                     "AND (:entryType IS NULL OR tl.entryType = :entryType)"
    )
    Page<TransactionLedger> findByAccountIdAndEntryType(
            @Param("accountId") Long accountId, 
            @Param("entryType") String entryType, 
            Pageable pageable);

    @Query("SELECT tl FROM TransactionLedger tl " +
           "JOIN FETCH tl.transaction t " +
           "WHERE tl.account.id = :accountId " +
           "AND tl.entryType = 'DEBIT' " +
           "AND tl.createdAt >= :startDate " +
           "ORDER BY tl.createdAt DESC")
    List<TransactionLedger> findRecentDebitsWithTransaction(
            @Param("accountId") Long accountId, 
            @Param("startDate") LocalDateTime startDate);

    @Query("SELECT COUNT(tl) > 0 FROM TransactionLedger tl " +
           "JOIN tl.transaction t " +
           "WHERE tl.account.id = :accountId " +
           "AND tl.entryType = 'DEBIT' " +
           "AND t.toAccountNumber = :toAccountNumber")
    boolean existsByAccountIdAndToAccountNumber(
        @Param("accountId") Long accountId,
        @Param("toAccountNumber") String toAccountNumber
    );
 
    @Query("SELECT tl FROM TransactionLedger tl " +
           "JOIN FETCH tl.transaction t " +
           "WHERE tl.account.id = :accountId " +
           "AND tl.createdAt >= :startDate " +
           "ORDER BY tl.createdAt ASC")  
    List<TransactionLedger> findTransactionsForAnalytics(
            @Param("accountId") Long accountId, 
            @Param("startDate") LocalDateTime startDate);
}