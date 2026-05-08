package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; 
import org.springframework.data.repository.query.Param; 
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
}