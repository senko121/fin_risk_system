package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserBehaviorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserBehaviorProfileRepository extends JpaRepository<UserBehaviorProfile, Long> {
    Optional<UserBehaviorProfile> findByUserId(Long userId);
}   