package com.datn.finrisk.core.services;

import com.datn.finrisk.core.entities.User;
import com.datn.finrisk.core.entities.UserSecurity;
import com.datn.finrisk.core.repository.UserRepository;
import com.datn.finrisk.core.repository.UserSecurityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSecurityRepository userSecurityRepository; // 🚀 Bổ sung kho bảo mật

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) throws Exception {
        // 1. Kiểm tra xem User có tồn tại không
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new Exception("Không tìm thấy người dùng!"));

        // 2. Tìm hồ sơ bảo mật của User này
        UserSecurity security = userSecurityRepository.findByUserId(userId)
                .orElseThrow(() -> new Exception("Lỗi hệ thống: Không tìm thấy hồ sơ bảo mật!"));

        // 3. Kiểm tra mật khẩu cũ có khớp không (Dùng BCrypt để so sánh)
        if (!passwordEncoder.matches(oldPassword, security.getPasswordHash())) {
            throw new Exception("Mật khẩu hiện tại không chính xác!");
        }

        // 4. Chặn đổi trùng mật khẩu cũ
        if (passwordEncoder.matches(newPassword, security.getPasswordHash())) {
            throw new Exception("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        // 5. Băm mật khẩu mới, cập nhật thời gian đổi và Lưu
        security.setPasswordHash(passwordEncoder.encode(newPassword));
        security.setLastPasswordChange(LocalDateTime.now()); // Lưu vết bảo mật
        
        userSecurityRepository.save(security);
    }
}