package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

//Transaction B2: được gọi để kiểm tra tài khoản nguồn có tồn tại không findByIdWithUserAndSecurity -> Transaction B3: TransactionLedgerRepository
//Transaction B2 Phase 2: tim thông tin tà khoản của người nhans tiên findByAccountNumber -> Transaction B9 Phase 2: Transactionservice
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    Optional<Account> findByAccountNumber(String accountNumber);
    
    Optional<Account> findByUser(User user);

    @Query("""
        SELECT a FROM Account a 
        LEFT JOIN FETCH a.user u 
        LEFT JOIN FETCH u.userSecurity 
        WHERE a.accountNumber IN :accountNumbers
    """)
    List<Account> findByAccountNumberIn(@Param("accountNumbers") Set<String> accountNumbers);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.user u LEFT JOIN FETCH u.userSecurity WHERE a.user.id = :userId")
    Optional<Account> findByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT a FROM Account a 
        LEFT JOIN FETCH a.user u 
        LEFT JOIN FETCH u.userSecurity 
        WHERE a.id = :accountId
    """)
    Optional<Account> findByIdWithUserAndSecurity(@Param("accountId") Long accountId);

    @Query("""
        SELECT a FROM Account a
        LEFT JOIN FETCH a.user u
        LEFT JOIN FETCH u.userSecurity
        WHERE a.accountNumber = :accountNumber
    """)
    Optional<Account> findByAccountNumberWithUser(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}