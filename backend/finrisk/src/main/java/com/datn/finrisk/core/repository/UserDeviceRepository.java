package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
 
    Optional<UserDevice> findByDeviceFingerprint(String deviceFingerprint);

    Optional<UserDevice> findByUserIdAndDeviceFingerprint(Long userId, String deviceFingerprint);

    List<UserDevice> findByUserId(Long userId);
}