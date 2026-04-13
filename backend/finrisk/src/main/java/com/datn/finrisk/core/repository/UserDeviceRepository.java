package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    // Tìm thiết bị dựa theo chuỗi Fingerprint (Mã máy)
    Optional<UserDevice> findByDeviceFingerprint(String deviceFingerprint);

    // Lấy tất cả danh sách thiết bị của 1 User cụ thể
    List<UserDevice> findByUserId(Long userId);
}