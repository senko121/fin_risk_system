package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    // Tìm tài khoản dựa trên số tài khoản
    Optional<Account> findByAccountNumber(String accountNumber);
}