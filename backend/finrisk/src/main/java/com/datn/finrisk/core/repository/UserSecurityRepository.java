package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserSecurity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSecurityRepository extends JpaRepository<UserSecurity, Long> {
    
    // Tìm kiếm thông tin bảo mật dựa trên ID của User
    Optional<UserSecurity> findByUserId(Long userId);
    
}