package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import com.datn.finrisk.core.entities.User; 
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    
    Optional<Account> findByUser(User user);

    List<Account> findByAccountNumberIn(java.util.Set<String> accountNumbers);
    
}